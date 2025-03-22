package com.server.anki.timeout.service;

import com.server.anki.config.MailOrderConfig;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.TimeoutType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时检查服务
 * 定时检查系统中的活跃订单是否存在超时情况
 */
@Service
public class OrderTimeoutChecker {
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutChecker.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private TimeoutFeeCalculator timeoutFeeCalculator;

    @Autowired
    private OrderTimeoutHandler timeoutHandler;

    @Autowired
    private MailOrderConfig mailOrderConfig;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 定时检查订单超时情况
     * 修改：使用独立事务处理每个订单，避免一个订单失败影响其他订单
     */
    @Scheduled(fixedRateString = "${mailorder.check-interval}")
    public void checkOrderTimeouts() {
        List<MailOrder> activeOrders = mailOrderRepository.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.COMPLETED)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        logger.debug("正在检查 {} 个活跃订单", activeOrders.size());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (MailOrder order : activeOrders) {
            try {
                transactionTemplate.execute(status -> {
                    try {
                        TimeoutResults.TimeoutCheckResult checkResult;
                        if (order.getDeliveryService() == DeliveryService.STANDARD) {
                            checkResult = checkStandardOrderTimeout(order, now);
                        } else {
                            checkResult = checkExpressOrderTimeout(order, now);
                        }

                        if (checkResult.status() != TimeoutStatus.NORMAL) {
                            timeoutHandler.handleTimeoutResult(order, checkResult);
                        }
                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.error("处理订单 {} 超时检查时发生错误: {}", order.getOrderNumber(), e.getMessage(), e);
                        return null;
                    }
                });
            } catch (Exception e) {
                logger.error("处理订单 {} 时事务执行失败: {}", order.getOrderNumber(), e.getMessage(), e);
            }
        }

        logger.info("定时订单超时检查完成");
    }

    /**
     * 检查标准订单的超时情况
     */
    private TimeoutResults.TimeoutCheckResult checkStandardOrderTimeout(MailOrder order, LocalDateTime now) {
        // 若尚未被接单，则不做超时检查
        if (order.getAssignedUser() == null) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        // 当订单状态为 PENDING 时，认为处于取件阶段
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            Duration elapsedTime = Duration.between(order.getCreatedAt(), now);
            int pickupTimeoutMinutes = mailOrderConfig.getServiceConfig(DeliveryService.STANDARD).getPickupTimeout();
            int elapsedMinutes = (int) elapsedTime.toMinutes();
            logger.debug("检查STANDARD订单取件超时: 订单号: {}, 已经过时间: {} 分钟, 阈值: {} 分钟",
                    order.getOrderNumber(), elapsedMinutes, pickupTimeoutMinutes);
            if (elapsedMinutes >= pickupTimeoutMinutes) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.PICKUP_TIMEOUT,
                        com.server.anki.timeout.enums.TimeoutType.PICKUP,
                        elapsedMinutes - pickupTimeoutMinutes);
            }
        } else if (order.getOrderStatus() == OrderStatus.IN_TRANSIT) {
            // 配送阶段：允许延长1小时，超出部分视为配送超时
            LocalDateTime allowedDeliveryTime = order.getDeliveryTime().plusHours(1);
            Duration overtime = Duration.between(allowedDeliveryTime, now);
            int overtimeMinutes = (int) overtime.toMinutes();
            logger.debug("检查STANDARD订单配送超时: 订单号: {}, 当前时间: {}, 允许截止时间: {}",
                    order.getOrderNumber(), now, allowedDeliveryTime);
            if (overtimeMinutes > 0) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.DELIVERY_TIMEOUT,
                        com.server.anki.timeout.enums.TimeoutType.DELIVERY,
                        overtimeMinutes);
            }
        }
        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

    /**
     * 检查快递订单的超时情况
     */
    private TimeoutResults.TimeoutCheckResult checkExpressOrderTimeout(MailOrder order, LocalDateTime now) {
        Duration elapsedTime;
        TimeoutType timeoutType;
        int timeoutMinutes;

        OrderStatus status = order.getOrderStatus();

        if (status == OrderStatus.PENDING || status == OrderStatus.ASSIGNED) {
            elapsedTime = Duration.between(order.getCreatedAt(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, TimeoutType.PICKUP);
            timeoutType = TimeoutType.PICKUP;
        } else if (status == OrderStatus.IN_TRANSIT) {
            elapsedTime = Duration.between(order.getDeliveryTime(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, TimeoutType.DELIVERY);
            timeoutType = TimeoutType.DELIVERY;
        } else if (status == OrderStatus.DELIVERED) {
            elapsedTime = Duration.between(order.getDeliveredDate(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, TimeoutType.CONFIRMATION);
            timeoutType = TimeoutType.CONFIRMATION;
        } else {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        int elapsedMinutes = (int) elapsedTime.toMinutes();
        double timeoutPercentage = (double) elapsedMinutes / timeoutMinutes;

        logger.debug("检查快递服务订单的超时情况: 订单号: {}, 已经过时间: {} 分钟, 限制时间: {} 分钟",
                order.getOrderNumber(), elapsedMinutes, timeoutMinutes);

        if (elapsedMinutes >= timeoutMinutes) {
            return new TimeoutResults.TimeoutCheckResult(
                    getTimeoutStatus(convertToOldTimeoutType(timeoutType), true),
                    convertToOldTimeoutType(timeoutType),
                    elapsedMinutes - timeoutMinutes
            );
        } else if (timeoutPercentage >= mailOrderConfig.getWarningThreshold()) {
            return new TimeoutResults.TimeoutCheckResult(
                    getTimeoutStatus(convertToOldTimeoutType(timeoutType), false),
                    convertToOldTimeoutType(timeoutType),
                    0
            );
        }

        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

    /**
     * 获取超时状态
     */
    private TimeoutStatus getTimeoutStatus(com.server.anki.timeout.enums.TimeoutType type, boolean isTimeout) {
        if (type == null) {
            return TimeoutStatus.NORMAL;
        }

        return switch (type) {
            case PICKUP -> isTimeout ? TimeoutStatus.PICKUP_TIMEOUT : TimeoutStatus.PICKUP_TIMEOUT_WARNING;
            case DELIVERY -> isTimeout ? TimeoutStatus.DELIVERY_TIMEOUT : TimeoutStatus.DELIVERY_TIMEOUT_WARNING;
            case CONFIRMATION -> isTimeout ? TimeoutStatus.CONFIRMATION_TIMEOUT : TimeoutStatus.CONFIRMATION_TIMEOUT_WARNING;
            default -> TimeoutStatus.NORMAL;
        };
    }

    /**
     * 将新的TimeoutType转换为旧的TimeoutType
     */
    private com.server.anki.timeout.enums.TimeoutType convertToOldTimeoutType(TimeoutType newType) {
        return switch (newType) {
            case PICKUP -> com.server.anki.timeout.enums.TimeoutType.PICKUP;
            case DELIVERY -> com.server.anki.timeout.enums.TimeoutType.DELIVERY;
            case CONFIRMATION -> com.server.anki.timeout.enums.TimeoutType.CONFIRMATION;
        };
    }
}