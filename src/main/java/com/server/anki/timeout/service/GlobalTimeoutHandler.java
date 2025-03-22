package com.server.anki.timeout.service;

import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.TimeoutType;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.service.PurchaseRequestService;
import com.server.anki.shopping.service.ShoppingOrderService;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全局超时处理服务
 * 负责处理各类订单的超时情况
 */
@Service
public class GlobalTimeoutHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTimeoutHandler.class);

    @Autowired
    private TimeoutFeeCalculator timeoutFeeCalculator;

    @Autowired
    private WalletService walletService;

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private TimeoutWarningRecordManager warningManager;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ShoppingOrderService shoppingOrderService;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    /**
     * 处理超时检查结果
     * @param order 订单
     * @param result 超时检查结果
     */
    @Transactional
    public void handleTimeoutResult(Timeoutable order, TimeoutResults.TimeoutCheckResult result) {
        if (result.status() == TimeoutStatus.NORMAL) {
            return;
        }

        try {
            switch (result.status()) {
                case PICKUP_TIMEOUT -> handlePickupTimeout(order);
                case DELIVERY_TIMEOUT -> handleDeliveryTimeout(order);
                case CONFIRMATION_TIMEOUT -> handleConfirmationTimeout(order);
                case PICKUP_TIMEOUT_WARNING, DELIVERY_TIMEOUT_WARNING, CONFIRMATION_TIMEOUT_WARNING ->
                        handleTimeoutWarning(order, result.status(), result.type());
                default -> logger.debug("当前状态无需处理: {}", result.status());
            }

            // 发送超时事件以更新统计
            if (result.isTimeout()) {
                Long userId = order.getAssignedUser() != null ? order.getAssignedUser().getId() : null;
                // 这里可以发布事件更新统计
                logger.info("发送超时事件，类型: {}, 用户ID: {}", result.type(), userId);
            }
        } catch (Exception e) {
            logger.error("处理订单 {} 的超时状态时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 处理取件超时
     * @param order 订单
     */
    @Transactional
    protected void handlePickupTimeout(Timeoutable order) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        logger.info("处理{}订单的取件超时: {}", orderType.getShortName(), order.getOrderNumber());

        try {
            // 根据订单类型处理超时罚款
            switch (orderType) {
                case MAIL_ORDER -> handleMailOrderPickupTimeout((MailOrder) order);
                case SHOPPING_ORDER -> handleShoppingOrderPickupTimeout((ShoppingOrder) order);
                case PURCHASE_REQUEST -> handlePurchaseRequestPickupTimeout((PurchaseRequest) order);
            }

            logger.info("{}订单 {} 取件超时处理完成", orderType.getShortName(), order.getOrderNumber());
        } catch (Exception e) {
            logger.error("处理{}订单 {} 取件超时错误: {}",
                    orderType.getShortName(), order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("取件超时处理失败", e);
        }
    }

    /**
     * 处理快递代拿订单的取件超时
     */
    private void handleMailOrderPickupTimeout(MailOrder order) {
        if (order.getAssignedUser() != null) {
            // 计算超时费用
            BigDecimal timeoutFee = calculateTimeoutFee(order, TimeoutType.PICKUP);

            if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
                processTimeoutFee(order, timeoutFee, "取件超时罚金");
            }

            // 调用服务方法重置订单
            mailOrderService.resetOrderForReassignment(order);
        }
    }

    /**
     * 处理商家订单的取件超时
     */
    private void handleShoppingOrderPickupTimeout(ShoppingOrder order) {
        if (order.getAssignedUser() != null) {
            // 计算超时费用
            BigDecimal timeoutFee = calculateTimeoutFee(order, TimeoutType.PICKUP);

            if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
                processTimeoutFee(order, timeoutFee, "商品取件超时罚金");
            }

            // 调用服务方法重置订单
            shoppingOrderService.resetOrderForReassignment(order);
        }
    }

    /**
     * 处理代购订单的取件超时
     */
    private void handlePurchaseRequestPickupTimeout(PurchaseRequest order) {
        if (order.getAssignedUser() != null) {
            // 计算超时费用
            BigDecimal timeoutFee = calculateTimeoutFee(order, TimeoutType.PICKUP);

            if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
                processTimeoutFee(order, timeoutFee, "代购超时罚金");
            }

            // 调用服务方法重置订单
            purchaseRequestService.resetOrderForReassignment(order);
        }
    }

    /**
     * 处理配送超时
     * @param order 订单
     */
    @Transactional
    protected void handleDeliveryTimeout(Timeoutable order) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        logger.info("处理{}订单的配送超时: {}", orderType.getShortName(), order.getOrderNumber());

        // 计算超时费用
        BigDecimal timeoutFee = calculateTimeoutFee(order, TimeoutType.DELIVERY);

        if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
            processTimeoutFee(order, timeoutFee, orderType.getShortName() + "配送超时罚金");
        }

        // 视情况进入平台介入
        boolean needsIntervention = shouldEnterIntervention(order);

        if (needsIntervention) {
            // 根据订单类型调用不同服务的平台介入方法
            switch (orderType) {
                case MAIL_ORDER -> mailOrderService.setOrderToIntervention(order.getOrderNumber());
                case SHOPPING_ORDER -> shoppingOrderService.handleOrderIntervention(order.getOrderNumber());
                case PURCHASE_REQUEST -> purchaseRequestService.handleOrderIntervention(order.getOrderNumber());
            }
        }
    }

    /**
     * 处理确认超时
     * @param order 订单
     */
    @Transactional
    protected void handleConfirmationTimeout(Timeoutable order) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        logger.info("处理{}订单的确认超时: {}", orderType.getShortName(), order.getOrderNumber());

        // 对于代购订单和快递订单，确认超时后自动完成
        if (orderType == TimeoutOrderType.MAIL_ORDER) {
            mailOrderService.completeOrder((MailOrder) order);
        } else if (orderType == TimeoutOrderType.PURCHASE_REQUEST) {
            purchaseRequestService.completeOrder((PurchaseRequest) order);
        }
        // 对于商家订单，确认超时后需平台介入
        else if (orderType == TimeoutOrderType.SHOPPING_ORDER) {
            shoppingOrderService.handleOrderIntervention(order.getOrderNumber());
        }
    }

    /**
     * 处理超时警告
     */
    protected void handleTimeoutWarning(Timeoutable order, TimeoutStatus status,
                                        com.server.anki.timeout.enums.TimeoutType type) {
        TimeoutResults.TimeoutWarningRecord existingWarning = warningManager.getWarningRecord(order.getId());

        // 检查是否需要发送新警告
        if (shouldSendNewWarning(existingWarning, status)) {
            // 转换为新的TimeoutType并估算潜在的超时费用
            TimeoutType newTimeoutType = convertToNewTimeoutType(type);
            BigDecimal estimatedFee = calculateTimeoutFee(order, newTimeoutType);

            // 发送警告通知
            sendTimeoutWarning(order, status, estimatedFee);

            // 记录警告
            warningManager.saveWarningRecord(order.getId(), status);

            // 更新订单状态
            updateOrderWarningStatus(order, status);

            logger.info("已发送超时警告，订单: {}, 状态: {}, 预估费用: {}",
                    order.getOrderNumber(), status, estimatedFee);
        }
    }

    /**
     * 判断是否需要发送新警告
     */
    private boolean shouldSendNewWarning(TimeoutResults.TimeoutWarningRecord existingWarning, TimeoutStatus status) {
        return existingWarning == null ||
                existingWarning.status() != status ||
                java.time.Duration.between(existingWarning.timestamp(), LocalDateTime.now()).toMinutes() >= 30;
    }

    /**
     * 将订单设置为平台介入状态
     */
    @Transactional
    protected void updateToIntervention(Timeoutable order) {
        TimeoutOrderType orderType = order.getTimeoutOrderType();
        logger.info("正在将{}订单 {} 更新为平台介入状态",
                orderType.getShortName(), order.getOrderNumber());

        try {
            order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
            order.setInterventionTime(LocalDateTime.now());

            saveOrder(order);

            // 通知订单所有者
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的%s #%s 已转入平台介入处理",
                            orderType.getShortName(), order.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            // 如果有配送员，也通知配送员
            if (order.getAssignedUser() != null) {
                messageService.sendMessage(
                        order.getAssignedUser(),
                        String.format("%s #%s 已转入平台介入处理",
                                orderType.getShortName(), order.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            logger.info("{}订单 {} 已成功更新为平台介入状态",
                    orderType.getShortName(), order.getOrderNumber());
        } catch (Exception e) {
            logger.error("将{}订单 {} 更新为平台介入状态时发生错误: {}",
                    orderType.getShortName(), order.getOrderNumber(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 保存不同类型的订单
     */
    private void saveOrder(Timeoutable order) {
        switch (order.getTimeoutOrderType()) {
            case MAIL_ORDER -> mailOrderService.updateMailOrder((MailOrder) order);
            case SHOPPING_ORDER -> shoppingOrderService.updateShoppingOrder((ShoppingOrder) order);
            case PURCHASE_REQUEST -> purchaseRequestService.updatePurchaseRequest((PurchaseRequest) order);
        }
    }

    /**
     * 判断是否应该进入平台介入状态
     */
    private boolean shouldEnterIntervention(Timeoutable order) {
        // 邮件订单，EXPRESS类型需要进入平台介入
        if (order.getTimeoutOrderType() == TimeoutOrderType.MAIL_ORDER) {
            MailOrder mailOrder = (MailOrder) order;
            return mailOrder.getDeliveryService() == com.server.anki.mailorder.enums.DeliveryService.EXPRESS;
        }

        // 其他订单，超时次数达到一定阈值需要平台介入
        int threshold = order.getTimeoutOrderType().getArchiveThreshold() - 1;
        return order.getTimeoutCount() >= threshold;
    }

    /**
     * 处理超时费用
     */
    private void processTimeoutFee(Timeoutable order, BigDecimal timeoutFee, String reason) {
        try {
            if (order.getAssignedUser() == null) {
                logger.warn("订单 {} 未分配配送员，无法处理超时费用", order.getOrderNumber());
                return;
            }

            String detailedReason = reason + " - 订单号: " + order.getOrderNumber();

            // 从配送员账户扣除费用
            walletService.addPendingFunds(
                    order.getAssignedUser(),
                    timeoutFee.negate(),
                    detailedReason
            );

            // 增加平台收入
            // 注意：不同类型订单的字段名可能不同，这里需要根据实际情况调整
            switch (order.getTimeoutOrderType()) {
                case MAIL_ORDER -> {
                    MailOrder mailOrder = (MailOrder) order;
                    mailOrder.setPlatformIncome(mailOrder.getPlatformIncome() + timeoutFee.doubleValue());
                }
                case SHOPPING_ORDER -> {
                    ShoppingOrder shoppingOrder = (ShoppingOrder) order;
                    shoppingOrder.setPlatformFee(shoppingOrder.getPlatformFee().add(timeoutFee));
                }
                case PURCHASE_REQUEST -> {
                    PurchaseRequest purchaseRequest = (PurchaseRequest) order;
                    purchaseRequest.setPlatformIncome(
                            purchaseRequest.getPlatformIncome() + timeoutFee.doubleValue());
                }
            }

            // 保存订单
            saveOrder(order);

            // 发送通知
            messageService.sendMessage(
                    order.getAssignedUser(),
                    String.format("由于%s，您的账户已扣除 %.2f 元罚款", reason, timeoutFee),
                    MessageType.BILLING_INFO,
                    null
            );

            logger.info("订单 {} 的超时费用 {} 已处理", order.getOrderNumber(), timeoutFee);
        } catch (Exception e) {
            logger.error("处理订单 {} 的超时费用时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 计算超时费用
     */
    private BigDecimal calculateTimeoutFee(Timeoutable order, TimeoutType timeoutType) {
        // 对于不同类型的订单可能需要不同的费用计算逻辑
        if (order.getTimeoutOrderType() == TimeoutOrderType.MAIL_ORDER) {
            return timeoutFeeCalculator.calculateTimeoutFee((MailOrder) order, timeoutType);
        }

        // 对于其他类型的订单，基于订单金额计算罚款
        BigDecimal baseFee = getBigDecimalByOrderType(order);

        if (timeoutType == TimeoutType.DELIVERY) {
            // 配送超时罚款加倍
            return baseFee.multiply(BigDecimal.valueOf(2));
        }

        return baseFee;
    }

    /**
     * 更新订单警告状态
     */
    private void updateOrderWarningStatus(Timeoutable order, TimeoutStatus status) {
        order.setTimeoutStatus(status);
        order.setTimeoutWarningSent(true);
        saveOrder(order);
    }

    /**
     * 根据订单类型获取基础罚款金额
     */
    private BigDecimal getBigDecimalByOrderType(Timeoutable order) {
        return switch (order.getTimeoutOrderType()) {
            case SHOPPING_ORDER -> {
                ShoppingOrder shoppingOrder = (ShoppingOrder) order;
                // 使用订单金额的5%作为基础罚款，最低10元
                BigDecimal fivePercent = shoppingOrder.getTotalAmount().multiply(BigDecimal.valueOf(0.05));
                yield fivePercent.compareTo(BigDecimal.TEN) < 0 ? BigDecimal.TEN : fivePercent;
            }
            case PURCHASE_REQUEST -> {
                PurchaseRequest purchaseRequest = (PurchaseRequest) order;
                // 使用订单金额的5%作为基础罚款，最低10元
                BigDecimal fivePercent = purchaseRequest.getTotalAmount().multiply(BigDecimal.valueOf(0.05));
                yield fivePercent.compareTo(BigDecimal.TEN) < 0 ? BigDecimal.TEN : fivePercent;
            }
            default -> BigDecimal.TEN; // 默认10元罚款
        };
    }

    /**
     * 发送超时警告
     */
    private void sendTimeoutWarning(Timeoutable order, TimeoutStatus status, BigDecimal estimatedFee) {
        if (order.getAssignedUser() == null) {
            return;
        }

        // 根据不同的状态发送不同的警告
        String warningMessage = switch (status) {
            case PICKUP_TIMEOUT_WARNING ->
                    String.format("您负责的%s #%s 即将超出取件时限，请尽快处理，否则可能产生 %.2f 元罚款",
                            order.getTimeoutOrderType().getShortName(),
                            order.getOrderNumber(),
                            estimatedFee);
            case DELIVERY_TIMEOUT_WARNING ->
                    String.format("您负责的%s #%s 即将超出配送时限，请加快配送，否则可能产生 %.2f 元罚款",
                            order.getTimeoutOrderType().getShortName(),
                            order.getOrderNumber(),
                            estimatedFee);
            default -> null;
        };

        if (warningMessage != null) {
            messageService.sendMessage(
                    order.getAssignedUser(),
                    warningMessage,
                    MessageType.ORDER_WARNING,
                    null
            );
        }

        // 对于确认超时警告，需要通知订单所有者
        if (status == TimeoutStatus.CONFIRMATION_TIMEOUT_WARNING) {
            String userWarningMessage =
                    String.format("您的%s #%s 已送达，请尽快确认，否则系统将在24小时后自动完成订单",
                            order.getTimeoutOrderType().getShortName(),
                            order.getOrderNumber());

            messageService.sendMessage(
                    order.getUser(),
                    userWarningMessage,
                    MessageType.ORDER_WARNING,
                    null
            );
        }
    }

    /**
     * 将旧的TimeoutType转换为新的TimeoutType
     */
    private TimeoutType convertToNewTimeoutType(com.server.anki.timeout.enums.TimeoutType oldType) {
        if (oldType == null) {
            logger.warn("收到空的超时类型，将默认使用 CONFIRMATION 类型");
            return TimeoutType.CONFIRMATION;
        }

        return switch (oldType) {
            case PICKUP -> TimeoutType.PICKUP;
            case DELIVERY -> TimeoutType.DELIVERY;
            case CONFIRMATION -> TimeoutType.CONFIRMATION;
            case INTERVENTION -> {
                logger.warn("收到干预类型的超时，将转换为 DELIVERY 类型进行处理");
                yield TimeoutType.DELIVERY;
            }
        };
    }
}