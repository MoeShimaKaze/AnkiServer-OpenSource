package com.server.anki.timeout.service;

import com.server.anki.config.MailOrderConfig;
import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.TimeoutType;
import com.server.anki.mailorder.entity.AbandonedOrder;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.AbandonedOrderRepository;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    private AbandonedOrderRepository abandonedOrderRepository;

    /**
     * 定时检查订单超时情况
     * 修改：使用独立事务处理每个订单，加强错误处理，避免重复处理已归档订单
     */
    @Scheduled(fixedRateString = "${mailorder.check-interval}")
    public void checkOrderTimeouts() {
        List<MailOrder> activeOrders = mailOrderRepository.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.COMPLETED &&
                        order.getOrderStatus() != OrderStatus.CANCELLED)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        logger.debug("正在检查 {} 个活跃订单", activeOrders.size());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        // 设置事务传播行为为REQUIRES_NEW，避免事务冲突
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 设置隔离级别为READ_COMMITTED，减少锁定
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        for (MailOrder order : activeOrders) {
            try {
                // 首先检查订单是否已归档，如果已归档则跳过处理
                if (isOrderArchived(order.getOrderNumber())) {
                    logger.debug("订单 {} 已归档，跳过处理", order.getOrderNumber());
                    skippedCount++;
                    continue;
                }

                // 再次查询确认订单状态
                MailOrder freshOrder = mailOrderRepository.findById(order.getId()).orElse(null);
                if (freshOrder == null ||
                        freshOrder.getOrderStatus() == OrderStatus.COMPLETED ||
                        freshOrder.getOrderStatus() == OrderStatus.CANCELLED) {
                    logger.debug("订单 {} 已完成或取消，跳过处理", order.getOrderNumber());
                    skippedCount++;
                    continue;
                }

                // 使用独立事务处理每个订单
                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        TimeoutResults.TimeoutCheckResult checkResult;
                        if (freshOrder.getDeliveryService() == DeliveryService.STANDARD) {
                            checkResult = checkStandardOrderTimeout(freshOrder, now);
                        } else {
                            checkResult = checkExpressOrderTimeout(freshOrder, now);
                        }

                        if (checkResult.status() != TimeoutStatus.NORMAL) {
                            timeoutHandler.handleTimeoutResult(freshOrder, checkResult);
                        }
                        return true;
                    } catch (Exception e) {
                        // 显式设置回滚
                        status.setRollbackOnly();
                        logger.error("处理订单 {} 超时检查时发生错误: {}",
                                freshOrder.getOrderNumber(), e.getMessage(), e);
                        return false;
                    }
                });

                if (Boolean.TRUE.equals(result)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                failureCount++;
                logger.error("处理订单 {} 时事务执行失败: {}",
                        order.getOrderNumber(), e.getMessage(), e);
            }

            processedCount++;
        }

        logger.info("定时订单超时检查完成，共处理 {} 个订单，成功 {}，失败 {}，跳过 {}",
                processedCount, successCount, failureCount, skippedCount);
    }

    /**
     * 检查订单是否已归档
     * @param orderNumber 订单编号
     * @return 如果订单已归档则返回true，否则返回false
     */
    private boolean isOrderArchived(UUID orderNumber) {
        try {
            // 查询废弃订单表，检查订单是否已归档
            List<AbandonedOrder> existingOrders = abandonedOrderRepository.findByOrderNumber(orderNumber);
            return !existingOrders.isEmpty();
        } catch (Exception e) {
            logger.warn("检查订单 {} 归档状态时发生错误: {}", orderNumber, e.getMessage());
            // 发生错误时假设订单未归档，以便后续检查
            return false;
        }
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