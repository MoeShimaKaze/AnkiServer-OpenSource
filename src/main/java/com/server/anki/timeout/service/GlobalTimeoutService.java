package com.server.anki.timeout.service;

import com.server.anki.config.MailOrderConfig;
import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.mailorder.entity.AbandonedOrder;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.AbandonedOrderRepository;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.repository.PurchaseRequestRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.enums.TimeoutType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.server.anki.fee.model.FeeTimeoutType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局超时管理服务
 * 负责所有订单类型的超时检查和处理
 */
@Service
public class GlobalTimeoutService {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTimeoutService.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private GlobalTimeoutHandler timeoutHandler;

    @Autowired
    private MailOrderConfig mailOrderConfig;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TimeoutFeeCalculator timeoutFeeCalculator;

    @Autowired
    private AbandonedOrderRepository abandonedOrderRepository;
    /**
     * 定时检查所有类型订单的超时情况
     * 修改：使用TransactionTemplate为每个订单创建独立事务，避免相互影响
     */
    @Scheduled(fixedRateString = "${timeout.check-interval:60000}")
    public void checkAllOrderTimeouts() {
        logger.info("开始全局超时检查任务");
        long startTime = System.currentTimeMillis();

        List<Timeoutable> activeOrders = new ArrayList<>();

        // 获取所有类型的活跃订单
        activeOrders.addAll(getActiveMailOrders());
        activeOrders.addAll(getActiveShoppingOrders());
        activeOrders.addAll(getActivePurchaseRequests());

        LocalDateTime now = LocalDateTime.now();
        logger.debug("正在检查 {} 个活跃订单", activeOrders.size());

        // 按订单类型优先级排序，确保高优先级订单先处理
        activeOrders.sort((o1, o2) ->
                o2.getTimeoutOrderType().getPriority() - o1.getTimeoutOrderType().getPriority());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        // 设置事务传播行为为REQUIRES_NEW，确保每个订单都在独立事务中处理
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 设置事务隔离级别为READ_COMMITTED，减少锁争用
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        for (Timeoutable order : activeOrders) {
            try {
                // 检查订单是否已归档，如果已归档则跳过处理
                if (isOrderArchived(order.getOrderNumber())) {
                    logger.debug("订单 {} 已归档，跳过处理", order.getOrderNumber());
                    skippedCount++;
                    continue;
                }

                Boolean result = transactionTemplate.execute(status -> {
                    try {
                        TimeoutResults.TimeoutCheckResult checkResult = checkOrderTimeout(order, now);
                        if (checkResult.status() != TimeoutStatus.NORMAL) {
                            timeoutHandler.handleTimeoutResult(order, checkResult);
                        }
                        return true;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        logger.error("处理订单 {} 超时检查时发生错误: {}",
                                order.getOrderNumber(), e.getMessage(), e);
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

            // 每处理100个订单记录一次日志
            if (processedCount % 100 == 0) {
                logger.debug("已处理 {}/{} 个订单", processedCount, activeOrders.size());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("全局超时检查任务完成，共处理 {} 个订单，成功 {}，失败 {}，跳过 {}，耗时 {} 毫秒",
                activeOrders.size(), successCount, failureCount, skippedCount, duration);
    }

    /**
     * 检查订单是否已归档
     * @param orderNumber 订单号
     * @return 是否已归档
     */
    private boolean isOrderArchived(UUID orderNumber) {
        try {
            List<AbandonedOrder> existingOrders = abandonedOrderRepository.findByOrderNumber(orderNumber);
            return !existingOrders.isEmpty();
        } catch (Exception e) {
            logger.warn("检查订单 {} 归档状态时发生错误: {}", orderNumber, e.getMessage());
            // 发生错误时假设订单未归档，以便后续处理
            return false;
        }
    }

    /**
     * 检查单个订单的超时情况
     * 整合了OrderTimeoutChecker的逻辑，根据订单类型选择适当的检查方法
     */
    private TimeoutResults.TimeoutCheckResult checkOrderTimeout(Timeoutable order, LocalDateTime now) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        OrderStatus status = order.getOrderStatus();

        // 对于不同类型的订单，使用不同的检查逻辑
        return switch (orderType) {
            case MAIL_ORDER -> {
                MailOrder mailOrder = (MailOrder) order;
                if (mailOrder.getDeliveryService() == DeliveryService.STANDARD) {
                    yield checkStandardOrderTimeout(mailOrder, now);
                } else {
                    yield checkExpressOrderTimeout(mailOrder, now);
                }
            }
            case SHOPPING_ORDER -> checkShoppingOrderTimeout(order, now);
            case PURCHASE_REQUEST -> checkPurchaseRequestTimeout(order, now);
        };
    }

    /**
     * 检查标准快递订单的超时情况
     * 从OrderTimeoutChecker迁移过来的逻辑
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
                        TimeoutType.PICKUP,
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
                        TimeoutType.DELIVERY,
                        overtimeMinutes);
            }
        }

        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

    /**
     * 检查快递订单的超时情况
     * 从OrderTimeoutChecker迁移过来的逻辑
     */
    private TimeoutResults.TimeoutCheckResult checkExpressOrderTimeout(MailOrder order, LocalDateTime now) {
        // 若尚未被接单，则不做超时检查
        if (order.getAssignedUser() == null) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        Duration elapsedTime;
        FeeTimeoutType timeoutType;
        int timeoutMinutes;

        OrderStatus status = order.getOrderStatus();

        if (status == OrderStatus.PENDING || status == OrderStatus.ASSIGNED) {
            // 取件阶段
            elapsedTime = Duration.between(order.getCreatedAt(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, FeeTimeoutType.PICKUP);
            timeoutType = FeeTimeoutType.PICKUP;

            logger.debug("检查EXPRESS订单取件超时: 订单号: {}, 已经过时间: {} 分钟, 限制时间: {} 分钟",
                    order.getOrderNumber(), elapsedTime.toMinutes(), timeoutMinutes);
        } else if (status == OrderStatus.IN_TRANSIT) {
            // 配送阶段
            elapsedTime = Duration.between(order.getDeliveryTime(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, FeeTimeoutType.DELIVERY);
            timeoutType = FeeTimeoutType.DELIVERY;

            logger.debug("检查EXPRESS订单配送超时: 订单号: {}, 当前时间: {}, 预计送达时间: {}, 已经过时间: {} 分钟",
                    order.getOrderNumber(), now, order.getDeliveryTime(), elapsedTime.toMinutes());
        } else if (status == OrderStatus.DELIVERED) {
            // 确认阶段
            if (order.getDeliveredDate() == null) {
                return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
            }
            elapsedTime = Duration.between(order.getDeliveredDate(), now);
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, FeeTimeoutType.CONFIRMATION);
            timeoutType = FeeTimeoutType.CONFIRMATION;

            logger.debug("检查EXPRESS订单确认超时: 订单号: {}, 已送达时间: {}, 已经过时间: {} 分钟",
                    order.getOrderNumber(), order.getDeliveredDate(), elapsedTime.toMinutes());
        } else {
            // 其他状态不做超时检查
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        int elapsedMinutes = (int) elapsedTime.toMinutes();
        double timeoutPercentage = (double) elapsedMinutes / timeoutMinutes;

        // 获取全局配置的警告阈值（默认80%）
        double warningThreshold = mailOrderConfig.getWarningThreshold();

        // 超出时间限制，标记为超时
        if (elapsedMinutes >= timeoutMinutes) {
            TimeoutType oldTimeoutType = convertToOldTimeoutType(timeoutType);
            return new TimeoutResults.TimeoutCheckResult(
                    getTimeoutStatus(oldTimeoutType, true),
                    oldTimeoutType,
                    elapsedMinutes - timeoutMinutes
            );
        }
        // 接近时间限制，发出警告
        else if (timeoutPercentage >= warningThreshold) {
            TimeoutType oldTimeoutType = convertToOldTimeoutType(timeoutType);
            return new TimeoutResults.TimeoutCheckResult(
                    getTimeoutStatus(oldTimeoutType, false),
                    oldTimeoutType,
                    0
            );
        }

        // 正常状态
        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

    /**
     * 获取所有活跃的快递代拿订单
     */
    private List<Timeoutable> getActiveMailOrders() {
        return mailOrderRepository.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.COMPLETED
                        && order.getOrderStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃的商家订单
     */
    private List<Timeoutable> getActiveShoppingOrders() {
        return shoppingOrderRepository.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.COMPLETED
                        && order.getOrderStatus() != OrderStatus.CANCELLED
                        && order.getOrderStatus() != OrderStatus.PAYMENT_PENDING)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃的代购订单
     */
    private List<Timeoutable> getActivePurchaseRequests() {
        return purchaseRequestRepository.findAll().stream()
                .filter(request -> request.getStatus() != OrderStatus.COMPLETED
                        && request.getStatus() != OrderStatus.CANCELLED
                        && request.getStatus() != OrderStatus.PAYMENT_PENDING)
                .collect(Collectors.toList());
    }

    /**
     * 将新的TimeoutType转换为旧的TimeoutType
     */
    private TimeoutType convertToOldTimeoutType(FeeTimeoutType newType) {
        return switch (newType) {
            case PICKUP -> TimeoutType.PICKUP;
            case DELIVERY -> TimeoutType.DELIVERY;
            case CONFIRMATION -> TimeoutType.CONFIRMATION;
        };
    }

    /**
     * 手动触发特定订单的超时检查
     */
    public TimeoutResults.TimeoutCheckResult manualCheckTimeout(Timeoutable order) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            return transactionTemplate.execute(status -> {
                try {
                    LocalDateTime now = LocalDateTime.now();
                    TimeoutResults.TimeoutCheckResult result = checkOrderTimeout(order, now);

                    if (result.status() != TimeoutStatus.NORMAL) {
                        timeoutHandler.handleTimeoutResult(order, result);
                    }

                    return result;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    logger.error("手动处理订单 {} 超时检查时发生错误: {}",
                            order.getOrderNumber(), e.getMessage(), e);
                    throw e;
                }
            });
        } catch (Exception e) {
            logger.error("手动处理订单 {} 时事务执行失败: {}",
                    order.getOrderNumber(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取超时状态
     */
    private TimeoutStatus getTimeoutStatus(TimeoutType type, boolean isTimeout) {
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
     * 检查商家订单的超时情况
     */
    private TimeoutResults.TimeoutCheckResult checkShoppingOrderTimeout(Timeoutable order, LocalDateTime now) {
        // 若尚未被接单，则不做超时检查
        if (order.getAssignedUser() == null) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        OrderStatus status = order.getOrderStatus();

        // 已接单但未开始配送
        if (status == OrderStatus.ASSIGNED) {
            Duration elapsedTime = Duration.between(order.getCreatedTime(), now);
            int pickupTimeoutMinutes = TimeoutOrderType.SHOPPING_ORDER.getDefaultTimeoutMinutes() / 2;
            int elapsedMinutes = (int) elapsedTime.toMinutes();

            logger.debug("检查商家订单取件超时: 订单号: {}, 已经过时间: {} 分钟, 阈值: {} 分钟",
                    order.getOrderNumber(), elapsedMinutes, pickupTimeoutMinutes);

            if (elapsedMinutes >= pickupTimeoutMinutes) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.PICKUP_TIMEOUT,
                        TimeoutType.PICKUP,
                        elapsedMinutes - pickupTimeoutMinutes);
            }
        }
        // 配送中但未送达
        else if (status == OrderStatus.IN_TRANSIT) {
            if (order.getExpectedDeliveryTime() == null) {
                return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
            }

            Duration overtime = Duration.between(order.getExpectedDeliveryTime(), now);
            int overtimeMinutes = (int) overtime.toMinutes();

            logger.debug("检查商家订单配送超时: 订单号: {}, 当前时间: {}, 预计送达时间: {}",
                    order.getOrderNumber(), now, order.getExpectedDeliveryTime());

            if (overtimeMinutes > 0) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.DELIVERY_TIMEOUT,
                        TimeoutType.DELIVERY,
                        overtimeMinutes);
            }
        }
        // 已送达但未确认
        else if (status == OrderStatus.DELIVERED) {
            if (order.getDeliveredTime() == null) {
                return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
            }

            Duration overtime = Duration.between(order.getDeliveredTime(), now.minusHours(24));
            int overtimeMinutes = (int) overtime.toMinutes();

            if (overtimeMinutes > 0) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.CONFIRMATION_TIMEOUT,
                        TimeoutType.CONFIRMATION,
                        overtimeMinutes);
            }
        }

        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

    /**
     * 检查代购订单的超时情况
     */
    private TimeoutResults.TimeoutCheckResult checkPurchaseRequestTimeout(Timeoutable order, LocalDateTime now) {
        PurchaseRequest request = (PurchaseRequest) order;

        // 若尚未被接单，检查截止时间
        if (order.getAssignedUser() == null && request.getDeadline() != null) {
            Duration overtime = Duration.between(request.getDeadline(), now);
            int overtimeMinutes = (int) overtime.toMinutes();

            if (overtimeMinutes > 0) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.CONFIRMATION_TIMEOUT,
                        TimeoutType.CONFIRMATION,
                        overtimeMinutes);
            }

            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        OrderStatus status = order.getOrderStatus();

        // 已接单但未开始采购
        if (status == OrderStatus.ASSIGNED) {
            Duration elapsedTime = Duration.between(order.getCreatedTime(), now);
            int pickupTimeoutMinutes = TimeoutOrderType.PURCHASE_REQUEST.getDefaultTimeoutMinutes() / 3;
            int elapsedMinutes = (int) elapsedTime.toMinutes();

            if (elapsedMinutes >= pickupTimeoutMinutes) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.PICKUP_TIMEOUT,
                        TimeoutType.PICKUP,
                        elapsedMinutes - pickupTimeoutMinutes);
            }
        }
        // 采购中但未送达
        else if (status == OrderStatus.IN_TRANSIT) {
            if (order.getExpectedDeliveryTime() == null) {
                return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
            }

            Duration overtime = Duration.between(order.getExpectedDeliveryTime(), now);
            int overtimeMinutes = (int) overtime.toMinutes();

            if (overtimeMinutes > 0) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.DELIVERY_TIMEOUT,
                        TimeoutType.DELIVERY,
                        overtimeMinutes);
            }
        }

        return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
    }

}