package com.server.anki.mailorder.service;

import com.server.anki.alipay.AlipayService;
import com.server.anki.config.MailOrderConfig;
import com.server.anki.config.RefundConfig;
import com.server.anki.fee.core.FeeContext;
import com.server.anki.fee.exception.FeeCalculationException;
import com.server.anki.fee.result.FeeDistribution;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.mailorder.OrderValidationResult;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.marketing.SpecialDateService;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.marketing.region.DeliveryRegion;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.user.UserService;
import com.server.anki.wallet.RefundMode;
import com.server.anki.wallet.entity.Wallet;
import com.server.anki.wallet.repository.WalletRepository;
import com.server.anki.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MailOrderService {

    private static final Logger logger = LoggerFactory.getLogger(MailOrderService.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    // 添加费率计算器的注入

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MailOrderConfig mailOrderConfig;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AbandonedOrderService abandonedOrderService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private RefundConfig refundConfig;

    // 注入关联服务
    @Autowired
    private RegionService regionService;

    @Autowired
    private SpecialDateService specialDateService;

    @Autowired
    private MailOrderLockService mailOrderLockService;

    @Autowired
    private OrderValidationService orderValidationService;

    @Autowired
    private FeeContext feeContext;

    @Autowired
    private MessageService messageService;

    private final AlipayService alipayService;

    @Autowired
    public MailOrderService(@Lazy AlipayService alipayService) {
        this.alipayService = alipayService;
    }

    /**
     * 创建需要支付的邮件订单
     * 使用统一费率计算器计算订单费用
     */
    @Transactional
    public PaymentResponse createMailOrderWithPayment(MailOrder mailOrder) {
        logger.info("创建需支付的邮件订单，用户ID: {}", mailOrder.getUser().getId());

        // 执行完整的订单验证
        OrderValidationResult validationResult =
                orderValidationService.validateOrderCompletely(mailOrder);

        if (!validationResult.isValid()) {
            logger.error("订单验证失败: {}", validationResult.message());
            throw new IllegalArgumentException(validationResult.message());
        }

        // 使用统一费率计算器计算费用
        try {
            // 调用费用计算上下文计算费用
            FeeResult feeResult = feeContext.calculateFee(mailOrder);

            // 设置订单费用信息
            mailOrder.setFee(feeResult.getTotalFee().doubleValue());

            // 从费用分配结果中获取收入分配
            FeeDistribution distribution = feeResult.getDistribution();
            mailOrder.setUserIncome(distribution.getDeliveryIncome().doubleValue());
            mailOrder.setPlatformIncome(distribution.getPlatformIncome().doubleValue());

            logger.debug("订单费用计算完成: 总费用={}, 用户收入={}, 平台收入={}",
                    feeResult.getTotalFee(),
                    distribution.getDeliveryIncome(),
                    distribution.getPlatformIncome());

        } catch (Exception e) {
            logger.error("订单费用计算失败: {}", e.getMessage());
            throw new RuntimeException("订单费用计算失败: " + e.getMessage());
        }

        // 设置初始状态为等待支付
        mailOrder.setOrderStatus(OrderStatus.PAYMENT_PENDING);

        // 根据配送类型设置预计送达时间
        if (mailOrder.getDeliveryService() == DeliveryService.EXPRESS) {
            mailOrder.setDeliveryTime(LocalDateTime.now()
                    .plusMinutes(mailOrderConfig.getDeliveryTime(mailOrder.getDeliveryService())));
        }

        // 保存订单
        MailOrder savedOrder = mailOrderRepository.save(mailOrder);

        // 创建支付订单
        PaymentResponse paymentResponse = alipayService.createMailOrderPayment(savedOrder);

        // 发送订单创建通知
        sendOrderCreationNotification(savedOrder);

        return paymentResponse;
    }

    /**
     * 订单费用估算方法
     * 使用统一费率计算器进行预估
     */
    @SuppressWarnings("unused")
    public double estimateOrderFee(double weight, boolean isLargeItem,
                                   DeliveryService deliveryService,
                                   double deliveryDistance) {
        logger.debug("开始估算订单费用: 重量={}kg, 大件={}, 服务类型={}, 距离={}km",
                weight, isLargeItem, deliveryService, deliveryDistance);

        try {
            // 验证输入参数
            if (weight <= 0 || deliveryDistance < 0) {
                throw new IllegalArgumentException("无效的重量或距离参数");
            }

            // 创建临时订单对象用于费用估算
            MailOrder tempOrder = new MailOrder();
            tempOrder.setWeight(weight);
            tempOrder.setLargeItem(isLargeItem);
            tempOrder.setDeliveryService(deliveryService);
            tempOrder.setDeliveryDistance(deliveryDistance);

            // 使用统一费率计算器进行估算
            FeeResult estimatedFee = feeContext.estimateFee(tempOrder);

            double estimatedAmount = estimatedFee.getTotalFee().doubleValue();

            logger.debug("费用估算完成: {}", estimatedAmount);

            return estimatedAmount;

        } catch (Exception e) {
            logger.error("费用估算时发生错误: {}", e.getMessage());
            throw new FeeCalculationException("费用估算失败: " + e.getMessage());
        }
    }

    /**
     * 发送订单创建通知
     * 根据配送类型发送不同的通知内容
     */
    private void sendOrderCreationNotification(MailOrder order) {
        if (order.getDeliveryService() == DeliveryService.EXPRESS) {
            // 快递服务通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的快递订单 #%s 已创建成功，请在30分钟内完成支付。预计送达时间：%s",
                            order.getOrderNumber(),
                            order.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm"))),
                    MessageType.ORDER_PAYMENT_CREATED,
                    null
            );
        } else {
            // 普通订单通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的订单 #%s 已创建成功，请在30分钟内完成支付",
                            order.getOrderNumber()),
                    MessageType.ORDER_PAYMENT_CREATED,
                    null
            );
        }
    }
    /**
     * 处理支付成功后的订单状态更新
     */
    @Transactional
    public void processAfterPaymentSuccess(UUID orderNumber) {
        logger.info("处理支付成功后的订单状态: {}", orderNumber);

        MailOrder mailOrder = findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("邮件订单不存在"));

        // 根据配送类型设置订单状态和处理逻辑
        if (mailOrder.getDeliveryService() == DeliveryService.EXPRESS) {
            mailOrder.setOrderStatus(OrderStatus.ASSIGNED);
            // 分配快递员
            assignExpressOrderToMessenger(mailOrder);

            // 如果已分配快递员，发送通知
            if (mailOrder.getAssignedUser() != null) {
                messageService.sendMessage(
                        mailOrder.getAssignedUser(),
                        String.format("新的快递订单 #%s 已分配给您",
                                mailOrder.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }
        } else {
            mailOrder.setOrderStatus(OrderStatus.PENDING);

            messageService.sendMessage(
                    mailOrder.getUser(),
                    String.format("您的订单 #%s 已进入待处理状态，等待接单",
                            mailOrder.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );
        }

        mailOrderRepository.save(mailOrder);
    }

    public Optional<MailOrder> findByOrderNumber(UUID orderNumber) {
        logger.debug("Searching for mail order with order number: {}", orderNumber);
        return mailOrderRepository.findByOrderNumber(orderNumber);
    }

    @Transactional
    public void deleteAndAbandonOrder(UUID orderNumber) {
        logger.info("准备删除并归档订单: {}", orderNumber);

        MailOrder order = mailOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    logger.warn("尝试删除不存在的订单: {}", orderNumber);
                    return new RuntimeException("订单不存在");
                });

        try {
            // 调用归档服务进行订单归档
            abandonedOrderService.archiveAndDeleteOrder(order);

            // 删除原订单
            mailOrderRepository.delete(order);

            logger.info("订单 {} 删除并归档完成", orderNumber);
        } catch (Exception e) {
            logger.error("订单删除归档过程中发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("订单删除归档失败: " + e.getMessage());
        }
    }

    public MailOrder updateMailOrder(MailOrder mailOrder) {
        logger.info("Updating mail order: {}", mailOrder.getOrderNumber());

        // 验证订单信息
        OrderValidationResult validationResult =
                orderValidationService.validateOrderCompletely(mailOrder);

        if (!validationResult.isValid()) {
            logger.error("订单更新验证失败: {}", validationResult.message());
            throw new IllegalArgumentException(validationResult.message());
        }

        return mailOrderRepository.save(mailOrder);
    }

    public void assignExpressOrderToMessenger(MailOrder mailOrder) {
        if (mailOrder.getDeliveryService() == DeliveryService.EXPRESS) {
            logger.info("Assigning express order {} to a messenger", mailOrder.getOrderNumber());
            List<User> messengers = userRepository.findByUserGroup("messenger");
            if (!messengers.isEmpty()) {
                User selectedMessenger = findBestMessengerForAssignment(messengers);
                mailOrder.setAssignedUser(selectedMessenger);
                mailOrder.setOrderStatus(OrderStatus.ASSIGNED);

                // 添加分配快递员通知
                messageService.sendMessage(
                        mailOrder.getUser(),
                        String.format("您的快递订单 #%s 已分配给快递员",
                                mailOrder.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );

                messageService.sendMessage(
                        selectedMessenger,
                        String.format("新的快递订单 #%s 已分配给您",
                                mailOrder.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );

                mailOrderRepository.save(mailOrder);
                logger.info("Express order {} assigned to messenger: {}",
                        mailOrder.getOrderNumber(), selectedMessenger.getId());
            } else {
                logger.warn("No messengers available to assign express order: {}",
                        mailOrder.getOrderNumber());
            }
        }
    }

    private User findBestMessengerForAssignment(List<User> messengers) {
        logger.debug("Finding best messenger for assignment among {} messengers", messengers.size());
        Map<User, Long> messengerOrderCounts = new HashMap<>();

        // Initialize all messenger order counts to 0
        for (User messenger : messengers) {
            messengerOrderCounts.put(messenger, 0L);
        }

        // Get all currently assigned orders
        List<MailOrder> assignedOrders = mailOrderRepository.findByOrderStatus(OrderStatus.ASSIGNED);

        // Count current orders for each messenger
        for (MailOrder order : assignedOrders) {
            User assignedUser = order.getAssignedUser();
            if (messengerOrderCounts.containsKey(assignedUser)) {
                messengerOrderCounts.put(assignedUser, messengerOrderCounts.get(assignedUser) + 1);
            }
        }

        // Find the messenger with the least orders
        User selectedMessenger = messengerOrderCounts.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(messengers.get(0));

        logger.debug("Selected messenger {} for assignment", selectedMessenger.getId());
        return selectedMessenger;
    }

    @Transactional
    public void completeOrder(MailOrder mailOrder) {
        logger.info("开始完成订单处理: {}", mailOrder.getOrderNumber());
        mailOrder.setOrderStatus(OrderStatus.COMPLETED);
        mailOrder.setCompletionDate(LocalDateTime.now());

        // 处理分配用户的待处理余额
        if (mailOrder.getAssignedUser() != null) {
            BigDecimal userIncome = BigDecimal.valueOf(mailOrder.getUserIncome());
            logger.info("正在为用户 {} 添加待处理资金 {}",
                    mailOrder.getAssignedUser().getId(), userIncome);
            try {
                // 使用 WalletService 发送消息
                walletService.addPendingFunds(
                        mailOrder.getAssignedUser(),
                        userIncome,
                        "订单完成收入: " + mailOrder.getOrderNumber()
                );

                // 添加消息通知
                messageService.sendMessage(
                        mailOrder.getAssignedUser(),
                        String.format("您的订单 #%s 已完成，收入 %.2f 元已添加到待处理余额",
                                mailOrder.getOrderNumber(), userIncome),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            } catch (Exception e) {
                logger.error("为用户添加待处理资金时出错 {}: {}",
                        mailOrder.getAssignedUser().getId(), e.getMessage(), e);
                throw new RuntimeException("添加待处理资金失败", e);
            }
        }

        // 处理系统账户余额
        User systemAccount = userService.getOrCreateSystemAccount();
        BigDecimal platformIncome = BigDecimal.valueOf(mailOrder.getPlatformIncome());
        logger.info("正在为系统账户添加待处理资金 {}", platformIncome);
        try {
            // 使用 WalletService 发送消息
            walletService.addPendingFunds(
                    systemAccount,
                    platformIncome,
                    "平台订单收入: " + mailOrder.getOrderNumber()
            );
        } catch (Exception e) {
            logger.error("为系统账户添加待处理资金时出错: {}", e.getMessage(), e);
            throw new RuntimeException("添加系统账户待处理资金失败", e);
        }

        mailOrderRepository.save(mailOrder);
        logger.info("订单 {} 完成处理", mailOrder.getOrderNumber());
    }

    @Transactional
    public void setOrderToIntervention(UUID orderNumber) {
        logger.info("Setting order {} to intervention status", orderNumber);
        MailOrder order = mailOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    logger.error("Order not found: {}", orderNumber);
                    return new RuntimeException("Order not found");
                });
        order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
        order.setInterventionTime(LocalDateTime.now());

        // 添加平台干预通知
        messageService.sendMessage(
                order.getUser(),
                String.format("您的订单 #%s 已进入平台干预状态，我们会尽快处理",
                        order.getOrderNumber()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        if (order.getAssignedUser() != null) {
            messageService.sendMessage(
                    order.getAssignedUser(),
                    String.format("订单 #%s 已进入平台干预状态",
                            order.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );
        }

        mailOrderRepository.save(order);
        logger.info("Order {} set to intervention status", orderNumber);
    }

    @Transactional
    public void setOrderToRefunding(UUID orderNumber) {
        logger.info("正在将订单设置为退款状态: {}", orderNumber);
        MailOrder order = mailOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("未找到订单"));

        if (order.getOrderStatus() != OrderStatus.PLATFORM_INTERVENTION) {
            throw new IllegalStateException("只有处于平台干预状态的订单可以设置为退款状态");
        }

        order.setOrderStatus(OrderStatus.REFUNDING);
        order.setRefundRequestedAt(LocalDateTime.now());

        // 添加退款状态变更通知
        messageService.sendMessage(
                order.getUser(),
                String.format("您的订单 #%s 已进入退款处理状态", order.getOrderNumber()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        messageService.sendMessage(
                order.getAssignedUser(),
                String.format("订单 #%s 已进入退款处理状态", order.getOrderNumber()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        mailOrderRepository.save(order);
        logger.info("订单 {} 已设置为退款状态", orderNumber);
    }

    /**
     * 处理订单退款
     * 包含以下步骤:
     * 1. 验证订单状态
     * 2. 检查账户余额
     * 3. 处理退款资金划转
     * 4. 处理平台退款(如果需要)
     * 5. 更新订单状态
     * 6. 发送通知
     */
    @Transactional
    public void processRefund(UUID orderNumber) {
        logger.info("正在处理订单退款: {}", orderNumber);
        MailOrder order = mailOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("未找到订单"));

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.REFUNDING) {
            logger.error("订单 {} 状态错误,当前状态: {}", orderNumber, order.getOrderStatus());
            throw new IllegalStateException("只有处于退款状态的订单才能进行退款处理");
        }

        try {
            // 获取相关用户信息和退款金额
            User assignedUser = order.getAssignedUser();
            User orderUser = order.getUser();
            BigDecimal refundAmount = BigDecimal.valueOf(order.getUserIncome());

            // 验证配送员信息
            if (assignedUser == null) {
                logger.warn("订单 {} 没有分配的配送员,无法处理退款", orderNumber);
                return;
            }

            // 检查账户余额
            Wallet assignedUserWallet = walletRepository.findByUser(assignedUser)
                    .orElseThrow(() -> new RuntimeException("配送员钱包不存在"));

            BigDecimal totalBalance = assignedUserWallet.getBalance()
                    .add(assignedUserWallet.getPendingBalance());

            // 余额不足处理
            if (totalBalance.compareTo(refundAmount) < 0) {
                logger.warn("订单 {} 退款失败: 配送员 {} 账户余额不足",
                        orderNumber, assignedUser.getId());

                // 锁定订单
                mailOrderLockService.lockOrder(order.getId(), "退款资金不足");

                // 发送余额不足通知
                messageService.sendMessage(
                        assignedUser,
                        String.format("订单 #%s 退款处理失败：账户余额不足，请及时处理", orderNumber),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
                return;
            }

            // 处理退款资金划转
            try {
                // 第一步：扣除配送员账户余额
                walletService.addPendingFunds(
                        assignedUser,
                        refundAmount.negate(),
                        String.format("订单退款扣除: %s", orderNumber)
                );

                // 第二步：将退款转给订单用户
                walletService.transferFunds(
                        assignedUser,
                        orderUser,
                        refundAmount,
                        String.format("订单退款: %s", orderNumber)
                );

                logger.info("订单 {} 的退款资金划转已完成,金额: {}", orderNumber, refundAmount);

                // 处理平台退款(如果启用了全额退款模式)
                if (refundConfig.getMode() == RefundMode.FULL) {
                    processPlatformRefund(order, orderUser);
                }

                // 更新订单状态
                order.setOrderStatus(OrderStatus.REFUNDED);
                order.setRefundDate(LocalDateTime.now());
                mailOrderRepository.save(order);

                // 发送退款成功通知给订单用户
                messageService.sendMessage(
                        orderUser,
                        String.format("您的订单 #%s 退款已处理完成，退款金额 %.2f 元已到账",
                                orderNumber, refundAmount),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );

                // 发送退款成功通知给配送员
                messageService.sendMessage(
                        assignedUser,
                        String.format("订单 #%s 的退款已处理完成，已扣除金额 %.2f 元",
                                orderNumber, refundAmount),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );

                logger.info("订单 {} 退款处理完成,退款金额: {}", orderNumber, refundAmount);

            } catch (Exception e) {
                logger.error("处理订单 {} 退款资金划转时发生错误: {}",
                        orderNumber, e.getMessage(), e);
                throw new RuntimeException("退款资金划转失败", e);
            }

        } catch (Exception e) {
            logger.error("处理订单 {} 退款时发生错误: {}",
                    orderNumber, e.getMessage(), e);
            throw new RuntimeException("退款处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理平台退款
     * 根据配置计算并处理平台应退金额
     */
    private void processPlatformRefund(MailOrder order, User orderUser) {
        BigDecimal platformRefundAmount = BigDecimal.valueOf(order.getPlatformIncome())
                .multiply(BigDecimal.valueOf(refundConfig.getPlatformPercentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (platformRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            User systemUser = userService.getOrCreateSystemAccount();

            walletService.transferFunds(
                    systemUser,
                    orderUser,
                    platformRefundAmount,
                    String.format("平台退款: %s", order.getOrderNumber())
            );

            logger.info("订单 {} 的平台退款已完成,金额: {}",
                    order.getOrderNumber(), platformRefundAmount);
        }
    }

    // ---------- 动态查询方法 ----------

    /**
     * 根据经纬度查询区域名称（使用高德坐标格式）
     */
    public String getRegionNameByCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) return "未知区域";
        String amapCoordinate = String.format("%s,%s", longitude, latitude);
        return regionService.findRegionByCoordinate(amapCoordinate)
                .map(DeliveryRegion::getName)
                .orElse("未知区域");
    }

    /**
     * 根据配送时间查询适用的特殊日期
     */
    public SpecialDate getApplicableSpecialDate(LocalDateTime deliveryTime) {
        if (deliveryTime == null) return null;
        List<SpecialDate> activeDates = specialDateService.getActiveSpecialDates(deliveryTime.toLocalDate());
        return activeDates.stream().min((d1, d2) -> d2.getPriority() - d1.getPriority())
                .orElse(null);
    }

    /**
     * 根据配送时间查询适用的特殊时段（通过 SpecialDateService）
     */
    public SpecialTimeRange getApplicableTimeRange(LocalDateTime deliveryTime) {
        if (deliveryTime == null) return null;
        int hour = deliveryTime.getHour();
        List<SpecialTimeRange> activeRanges = specialDateService.findActiveTimeRangesByHour(hour);
        return activeRanges.stream().min((t1, t2) -> t2.getRateMultiplier().compareTo(t1.getRateMultiplier()))
                .orElse(null);
    }


    /**
     * 重置超时订单状态以便重新分配
     * 清除原配送员信息，更新相关状态和时间，记录超时次数
     *
     * @param order 需要重置的订单
     */
    @Transactional
    public void resetOrderForReassignment(MailOrder order) {
        logger.info("重置订单 {} 状态以便重新分配", order.getOrderNumber());
        try {
            // 增加超时计数
            order.setTimeoutCount(order.getTimeoutCount() + 1);
            logger.debug("订单 {} 超时次数更新为: {}", order.getOrderNumber(), order.getTimeoutCount());

            // 判断归档阈值：STANDARD为10次，EXPRESS为3次
            int archiveThreshold = order.getDeliveryService() == DeliveryService.STANDARD ? 10 : 3;
            if (order.getTimeoutCount() >= archiveThreshold) {
                // 超时次数达到阈值，归档订单
                archiveOrder(order);
                return;
            }

            // 若有原配送员，发送通知
            if (order.getAssignedUser() != null) {
                messageService.sendMessage(
                        order.getAssignedUser(),
                        String.format("由于您未在规定时间内完成取件，订单 #%s 已被系统收回（第 %d 次超时）",
                                order.getOrderNumber(), order.getTimeoutCount()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            // 重置订单状态
            order.setAssignedUser(null);
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);

            // 更新配送时间以便重新接单
            updateDeliveryTimeForReassignment(order);

            // 通知订单创建者
            String userMessage = String.format("您的订单 #%s 因超时（第 %d 次）正在重新分配配送员",
                    order.getOrderNumber(), order.getTimeoutCount());
            messageService.sendMessage(order.getUser(), userMessage, MessageType.ORDER_STATUS_UPDATED, null);

            mailOrderRepository.save(order);
            logger.info("订单 {} 状态重置完成，当前超时次数: {}", order.getOrderNumber(), order.getTimeoutCount());
        } catch (Exception e) {
            logger.error("重置订单 {} 状态错误: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("重置订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 归档订单：调用 AbandonedOrderService.archiveAndDeleteOrder 归档订单（使用提供的方法）
     */
    @Transactional
    public void archiveOrder(MailOrder order) {
        logger.info("归档订单: {}", order.getOrderNumber());
        try {
            abandonedOrderService.archiveAndDeleteOrder(order);
            logger.info("订单 {} 已归档", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("归档订单 {} 失败: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("订单归档失败: " + e.getMessage());
        }
    }

    /**
     * 更新订单的预计配送时间
     * 根据配送服务类型计算新的配送时间
     * @param order 需要更新的订单
     */
    private void updateDeliveryTimeForReassignment(MailOrder order) {
        // 获取配送服务相关配置
        int deliveryTimeMinutes = mailOrderConfig.getDeliveryTime(order.getDeliveryService());

        // 计算新的预计配送时间
        LocalDateTime newDeliveryTime = LocalDateTime.now().plusMinutes(deliveryTimeMinutes);

        // 更新订单配送时间
        order.setDeliveryTime(newDeliveryTime);

        logger.debug("订单 {} 更新预计配送时间为: {}",
                order.getOrderNumber(),
                newDeliveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * 获取分页的待处理订单
     */
    public Page<MailOrder> getPendingOrders(Pageable pageable) {
        logger.debug("获取分页待处理订单: 页码={}, 每页大小={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return mailOrderRepository.findByOrderStatus(OrderStatus.PENDING, pageable);
    }

    /**
     * 获取分页的用户订单
     */
    public Page<MailOrder> getUserOrders(Long userId, Pageable pageable) {
        logger.debug("获取用户{}的分页订单: 页码={}, 每页大小={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return mailOrderRepository.findByUserId(userId, pageable);
    }

    /**
     * 获取分页的已分配订单
     */
    public Page<MailOrder> getAssignedOrders(Long userId, Pageable pageable) {
        logger.debug("获取用户{}的分页已分配订单: 页码={}, 每页大小={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return mailOrderRepository.findByAssignedUserIdAndOrderStatusIn(
                userId,
                Arrays.asList(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT),
                pageable
        );
    }

    /**
     * 获取分页的所有订单
     */
    public Page<MailOrder> getAllOrders(Pageable pageable) {
        logger.debug("获取所有分页订单: 页码={}, 每页大小={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return mailOrderRepository.findAll(pageable);
    }
}