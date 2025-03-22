package com.server.anki.timeout.service;

import com.server.anki.config.MailOrderConfig;
import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.FeeTimeoutType;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
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

    @Autowired
    private OrderLockService orderLockService;

    /**
     * 定时检查所有类型订单的超时情况
     */
    @Scheduled(fixedRateString = "${timeout.check-interval:60000}")
    public void checkAllOrderTimeouts() {
        logger.info("开始全局超时检查任务");
        long startTime = System.currentTimeMillis();

        List<Timeoutable> activeOrders = new ArrayList<>();
        activeOrders.addAll(getActiveMailOrders());
        activeOrders.addAll(getActiveShoppingOrders());
        activeOrders.addAll(getActivePurchaseRequests());

        LocalDateTime now = LocalDateTime.now();
        logger.debug("正在检查 {} 个活跃订单", activeOrders.size());

        activeOrders.sort((o1, o2) ->
                o2.getTimeoutOrderType().getPriority() - o1.getTimeoutOrderType().getPriority());

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        int lockedCount = 0;
        int archivedCount = 0;

        // 记录已处理的订单ID，避免重复处理
        Set<UUID> processedOrderIds = new HashSet<>();

        for (Timeoutable order : activeOrders) {
            // 跳过已处理的订单
            if (processedOrderIds.contains(order.getOrderNumber())) {
                logger.debug("订单 {} 在此批次中已处理，跳过", order.getOrderNumber());
                skippedCount++;
                continue;
            }

            try {
                // 检查订单是否已归档
                if (isOrderArchived(order.getOrderNumber())) {
                    logger.debug("订单 {} 已归档，跳过处理", order.getOrderNumber());
                    archivedCount++;
                    processedOrderIds.add(order.getOrderNumber());
                    continue;
                }

                // 尝试获取锁
                if (!orderLockService.tryLock(order.getOrderNumber())) {
                    logger.debug("订单 {} 已被其他线程锁定，跳过处理", order.getOrderNumber());
                    lockedCount++;
                    continue;
                }

                try {
                    // 获取锁后再次检查归档状态
                    if (isOrderArchived(order.getOrderNumber())) {
                        logger.debug("获取锁后发现订单 {} 已归档，跳过处理", order.getOrderNumber());
                        archivedCount++;
                        processedOrderIds.add(order.getOrderNumber());
                        continue;
                    }

                    // 处理订单
                    Boolean result = getResult(order, now);
                    if (Boolean.TRUE.equals(result)) {
                        successCount++;
                    } else {
                        failureCount++;
                    }

                    // 标记为已处理
                    processedOrderIds.add(order.getOrderNumber());
                } finally {
                    // 释放锁
                    orderLockService.unlock(order.getOrderNumber());
                }

                processedCount++;
                if (processedCount % 100 == 0) {
                    logger.debug("已处理 {}/{} 个订单", processedCount, activeOrders.size());
                }
            } catch (Exception e) {
                failureCount++;
                logger.error("处理订单 {} 过程中发生未预期错误: {}",
                        order.getOrderNumber(), e.getMessage(), e);

                // 确保释放锁
                try {
                    if (orderLockService.isLockedByCurrentThread(order.getOrderNumber())) {
                        orderLockService.unlock(order.getOrderNumber());
                    }
                } catch (Exception ex) {
                    logger.error("尝试释放订单 {} 锁时发生错误: {}",
                            order.getOrderNumber(), ex.getMessage());
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("全局超时检查任务完成: 处理 {} 个订单, 成功 {}, 失败 {}, 跳过 {}, 锁定 {}, 已归档 {}, 耗时 {} 毫秒",
                activeOrders.size(), successCount, failureCount, skippedCount, lockedCount, archivedCount, duration);
    }

    @Nullable
    private Boolean getResult(Timeoutable order, LocalDateTime now) {
        // 先检查订单是否已归档，避免不必要的事务启动
        if (isOrderArchived(order.getOrderNumber())) {
            logger.debug("订单 {} 已归档，跳过处理", order.getOrderNumber());
            return true; // 返回成功，不需要处理
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        return transactionTemplate.execute(status -> {
            try {
                // 事务内再次检查订单是否已归档
                if (isOrderArchived(order.getOrderNumber())) {
                    logger.debug("事务内再次检查：订单 {} 已归档，跳过处理", order.getOrderNumber());
                    return true;
                }

                TimeoutResults.TimeoutCheckResult checkResult = checkOrderTimeout(order, now);
                if (checkResult.status() != TimeoutStatus.NORMAL) {
                    // 使用单独方法处理超时结果，隔离事务
                    handleTimeoutResultSafely(order, checkResult);
                }
                return true;
            } catch (Exception e) {
                logger.error("处理订单 {} 超时检查时发生错误: {}",
                        order.getOrderNumber(), e.getMessage(), e);

                // GIS数据错误不应导致事务回滚
                if (e.getMessage() != null &&
                        (e.getMessage().contains("GIS data") ||
                                e.getMessage().contains("st_geomfromtext"))) {
                    logger.warn("GIS数据错误，但不影响订单处理: {}", e.getMessage());
                    return true; // 不标记回滚
                }

                // 只有关键异常才回滚事务
                if (e instanceof DataIntegrityViolationException ||
                        e instanceof OptimisticLockingFailureException ||
                        e instanceof PessimisticLockingFailureException) {
                    status.setRollbackOnly();
                    return false;
                }

                return true; // 对于非关键错误，不回滚事务
            }
        });
    }

    // 添加新方法，安全处理超时结果
    private void handleTimeoutResultSafely(Timeoutable order, TimeoutResults.TimeoutCheckResult result) {
        try {
            // 再次检查订单是否已归档
            if (isOrderArchived(order.getOrderNumber())) {
                logger.info("处理超时前发现订单 {} 已归档，跳过超时处理", order.getOrderNumber());
                return;
            }

            timeoutHandler.handleTimeoutResult(order, result);
        } catch (Exception e) {
            logger.error("安全处理订单 {} 超时结果时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
            // 捕获所有异常，不影响主事务
        }
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
     * 修改：使用统一的超时检查逻辑
     */
    private TimeoutResults.TimeoutCheckResult checkOrderTimeout(Timeoutable order, LocalDateTime now) {
        // 检查订单是否已归档
        if (isOrderArchived(order.getOrderNumber())) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        TimeoutOrderType orderType = order.getTimeoutOrderType();

        // 对于不同类型的订单，使用不同的检查逻辑
        return switch (orderType) {
            case MAIL_ORDER -> checkMailOrderTimeout((MailOrder) order, now);
            case SHOPPING_ORDER -> checkShoppingOrderTimeout(order, now);
            case PURCHASE_REQUEST -> checkPurchaseRequestTimeout(order, now);
        };
    }

    /**
     * 统一的快递代拿订单超时检查
     * 同时支持 STANDARD 和 EXPRESS 配送方式
     */
    private TimeoutResults.TimeoutCheckResult checkMailOrderTimeout(MailOrder order, LocalDateTime now) {
        // 若尚未被接单，则不做超时检查
        if (order.getAssignedUser() == null) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        OrderStatus status = order.getOrderStatus();
        Duration elapsedTime;
        FeeTimeoutType timeoutType;
        int timeoutMinutes;

        // 根据订单状态和配送方式确定超时检查逻辑
        if (status == OrderStatus.PENDING || status == OrderStatus.ASSIGNED) {
            // 取件阶段
            elapsedTime = Duration.between(order.getCreatedAt(), now);
            timeoutType = FeeTimeoutType.PICKUP;

            // 根据配送方式获取不同的超时阈值
            if (order.getDeliveryService() == DeliveryService.STANDARD) {
                timeoutMinutes = mailOrderConfig.getServiceConfig(DeliveryService.STANDARD).getPickupTimeout();
            } else {
                timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, timeoutType);
            }

            logger.debug("检查{}订单取件超时: 订单号: {}, 已经过时间: {} 分钟, 限制时间: {} 分钟",
                    order.getDeliveryService(), order.getOrderNumber(),
                    elapsedTime.toMinutes(), timeoutMinutes);
        } else if (status == OrderStatus.IN_TRANSIT) {
            // 配送阶段
            timeoutType = FeeTimeoutType.DELIVERY;

            if (order.getDeliveryService() == DeliveryService.STANDARD) {
                // 标准配送：允许延长1小时
                LocalDateTime allowedDeliveryTime = order.getDeliveryTime().plusHours(1);
                elapsedTime = Duration.between(allowedDeliveryTime, now);
                timeoutMinutes = 0; // 直接用elapsed > 0判断是否超时

                logger.debug("检查STANDARD订单配送超时: 订单号: {}, 当前时间: {}, 允许截止时间: {}",
                        order.getOrderNumber(), now, allowedDeliveryTime);
            } else {
                // EXPRESS配送：使用计算器确定超时时间
                elapsedTime = Duration.between(order.getDeliveryTime(), now);
                timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, timeoutType);

                logger.debug("检查EXPRESS订单配送超时: 订单号: {}, 当前时间: {}, 预计送达时间: {}, 已经过时间: {} 分钟",
                        order.getOrderNumber(), now, order.getDeliveryTime(), elapsedTime.toMinutes());
            }
        } else if (status == OrderStatus.DELIVERED) {
            // 确认阶段
            if (order.getDeliveredDate() == null) {
                return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
            }

            elapsedTime = Duration.between(order.getDeliveredDate(), now);
            timeoutType = FeeTimeoutType.CONFIRMATION;

            // 确认阶段超时时间对所有配送方式都一样
            timeoutMinutes = (int) timeoutFeeCalculator.getTimeoutMinutes(order, timeoutType);

            logger.debug("检查{}订单确认超时: 订单号: {}, 已送达时间: {}, 已经过时间: {} 分钟",
                    order.getDeliveryService(), order.getOrderNumber(),
                    order.getDeliveredDate(), elapsedTime.toMinutes());
        } else {
            // 其他状态不做超时检查
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        int elapsedMinutes = (int) elapsedTime.toMinutes();

        // STANDARD配送在配送阶段使用特殊判断
        if (order.getDeliveryService() == DeliveryService.STANDARD &&
                status == OrderStatus.IN_TRANSIT) {
            // 超出允许的配送时间，标记为超时
            if (elapsedMinutes > 0) {
                TimeoutType oldTimeoutType = convertToOldTimeoutType(timeoutType);
                return new TimeoutResults.TimeoutCheckResult(
                        getTimeoutStatus(oldTimeoutType, true),
                        oldTimeoutType,
                        elapsedMinutes
                );
            }
            // 否则视为正常
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        // 所有其他情况使用统一的逻辑
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