package com.server.anki.timeout.service;

import com.server.anki.config.MailOrderConfig;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 定时检查所有类型订单的超时情况
     * 修改：使用TransactionTemplate为每个订单创建独立事务
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

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        for (Timeoutable order : activeOrders) {
            try {
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
        logger.info("全局超时检查任务完成，共处理 {} 个订单，成功 {}，失败 {}，耗时 {} 毫秒",
                activeOrders.size(), successCount, failureCount, duration);
    }

    /**
     * 检查单个订单的超时情况
     * @param order 订单实体
     * @param now 当前时间
     * @return 超时检查结果
     */
    private TimeoutResults.TimeoutCheckResult checkOrderTimeout(Timeoutable order, LocalDateTime now) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        OrderStatus status = order.getOrderStatus();

        // 对于不同类型的订单，使用不同的检查逻辑
        return switch (orderType) {
            case MAIL_ORDER -> checkMailOrderTimeout(order, now);
            case SHOPPING_ORDER -> checkShoppingOrderTimeout(order, now);
            case PURCHASE_REQUEST -> checkPurchaseRequestTimeout(order, now);
        };
    }

    /**
     * 检查快递代拿订单的超时情况
     */
    private TimeoutResults.TimeoutCheckResult checkMailOrderTimeout(Timeoutable order, LocalDateTime now) {
        // 若尚未被接单，则不做超时检查
        if (order.getAssignedUser() == null) {
            return new TimeoutResults.TimeoutCheckResult(TimeoutStatus.NORMAL, null, 0);
        }

        MailOrder mailOrder = (MailOrder) order;

        // 当订单状态为 PENDING 时，认为处于取件阶段
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            Duration elapsedTime = Duration.between(order.getCreatedTime(), now);
            int pickupTimeoutMinutes = mailOrderConfig.getServiceConfig(
                    mailOrder.getDeliveryService()).getPickupTimeout();
            int elapsedMinutes = (int) elapsedTime.toMinutes();

            logger.debug("检查快递订单取件超时: 订单号: {}, 已经过时间: {} 分钟, 阈值: {} 分钟",
                    order.getOrderNumber(), elapsedMinutes, pickupTimeoutMinutes);

            if (elapsedMinutes >= pickupTimeoutMinutes) {
                return new TimeoutResults.TimeoutCheckResult(
                        TimeoutStatus.PICKUP_TIMEOUT,
                        TimeoutType.PICKUP,
                        elapsedMinutes - pickupTimeoutMinutes);
            }
        } else if (order.getOrderStatus() == OrderStatus.IN_TRANSIT) {
            // 配送阶段：允许延长1小时，超出部分视为配送超时
            LocalDateTime allowedDeliveryTime = order.getExpectedDeliveryTime().plusHours(1);
            Duration overtime = Duration.between(allowedDeliveryTime, now);
            int overtimeMinutes = (int) overtime.toMinutes();

            logger.debug("检查快递订单配送超时: 订单号: {}, 当前时间: {}, 允许截止时间: {}",
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

    /**
     * 获取所有活跃的快递代拿订单
     * @return 符合超时检查条件的代拿订单列表
     */
    private List<Timeoutable> getActiveMailOrders() {
        return mailOrderRepository.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.COMPLETED
                        && order.getOrderStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃的商家订单
     * @return 符合超时检查条件的商家订单列表
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
     * @return 符合超时检查条件的代购订单列表
     */
    private List<Timeoutable> getActivePurchaseRequests() {
        return purchaseRequestRepository.findAll().stream()
                .filter(request -> request.getStatus() != OrderStatus.COMPLETED
                        && request.getStatus() != OrderStatus.CANCELLED
                        && request.getStatus() != OrderStatus.PAYMENT_PENDING)
                .collect(Collectors.toList());
    }

    /**
     * 手动触发特定订单的超时检查
     * @param order 要检查的订单
     * @return 超时检查结果
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
}