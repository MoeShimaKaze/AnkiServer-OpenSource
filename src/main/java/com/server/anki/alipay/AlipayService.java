package com.server.anki.alipay;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.server.anki.config.AlipayConfig;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.pay.payment.*;
import com.server.anki.pay.timeout.PaymentTimeoutService;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.repository.ProductRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.shopping.service.PurchaseRequestService;
import com.server.anki.shopping.service.ShoppingOrderService;
import com.server.anki.user.User;
import com.server.anki.utils.DistributedLockHelper;
import com.server.anki.wallet.entity.WithdrawalOrder;
import com.server.anki.wallet.repository.WithdrawalOrderRepository;
import com.server.anki.wallet.service.WalletService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 支付宝支付服务
 * 负责处理与支付宝接口的所有交互，包括支付、转账和提现
 */
@Service
public class AlipayService {
    private static final Logger logger = LoggerFactory.getLogger(AlipayService.class);

    @Autowired
    private DistributedLockHelper distributedLockHelper;

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayConfig alipayConfig;

    @Autowired
    private PaymentTimeoutService paymentTimeoutService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private ShoppingOrderService shoppingOrderService;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private WithdrawalOrderRepository withdrawalOrderRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private ProductRepository productRepository;
    /**
     * 提现响应类
     * 封装支付宝提现API的响应信息
     */
    @Setter
    @Getter
    public static class AlipayWithdrawalResponse {
        private boolean success;
        private String outBizNo;         // 商户转账订单号
        private String orderId;          // 支付宝转账订单号
        private String errorMessage;     // 错误信息
        private String status;           // 转账状态
    }

    /**
     * 生成支付订单号
     */
    private String generatePaymentOrderNumber() {
        return String.format("PAY%d%04d",
                System.currentTimeMillis(),
                new Random().nextInt(10000));
    }

    /**
     * 创建邮件订单的支付订单
     * 使用新的订单类型和订单信息字段
     */
    @Transactional
    public PaymentResponse createMailOrderPayment(MailOrder mailOrder) {
        String orderIdentifier = mailOrder.getOrderNumber().toString();
        String lockKey = DistributedLockHelper.getPaymentLockKey(orderIdentifier);

        try {
            // 尝试获取分布式锁，等待5秒
            if (!distributedLockHelper.tryLock(lockKey)) {
                logger.warn("订单正在处理中，请勿重复提交，订单号: {}", mailOrder.getOrderNumber());
                throw new RuntimeException("订单正在处理中，请勿重复提交");
            }

            // 检查是否存在未完成的支付订单
            Optional<PaymentOrder> existingOrder = findValidPaymentOrder(OrderType.MAIL_ORDER, orderIdentifier);
            if (existingOrder.isPresent()) {
                logger.info("订单已存在有效的支付订单，订单号: {}", mailOrder.getOrderNumber());

                try {
                    // 为已存在的订单生成支付表单和支付链接
                    String payForm = createOrderPayment(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            mailOrder
                    );

                    // 为已存在的订单生成支付链接
                    String payUrl = createPaymentUrl(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            "快递订单支付",
                            String.format("从%s到%s的快递服务",
                                    mailOrder.getPickupAddress(),
                                    mailOrder.getDeliveryAddress())
                    );

                    return new PaymentResponse(
                            existingOrder.get().getOrderNumber(),
                            payForm,
                            payUrl,
                            existingOrder.get().getAmount(),
                            existingOrder.get().getExpireTime()
                    );
                } catch (AlipayApiException e) {
                    logger.error("生成支付信息失败: {}", e.getMessage(), e);
                    throw new RuntimeException("生成支付信息失败: " + e.getMessage());
                }
            }

            // 创建新的支付订单
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setOrderNumber(generatePaymentOrderNumber());
            paymentOrder.setUser(mailOrder.getUser());
            paymentOrder.setAmount(BigDecimal.valueOf(mailOrder.getFee()));
            paymentOrder.setStatus(PaymentStatus.WAITING);

            // 设置新字段
            paymentOrder.setOrderType(OrderType.MAIL_ORDER);
            paymentOrder.setOrderInfo(orderIdentifier);

            paymentOrder.setCreatedTime(LocalDateTime.now());
            paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(30));

            PaymentOrder savedPaymentOrder = paymentOrderRepository.save(paymentOrder);

            // 设置支付超时监控
            paymentTimeoutService.setPaymentTimeout(savedPaymentOrder.getOrderNumber());

            try {
                // 创建支付宝支付表单
                String payForm = createOrderPayment(
                        paymentOrder.getAmount(),
                        paymentOrder.getOrderNumber(),
                        mailOrder
                );

                // 创建支付链接
                String payUrl = createPaymentUrl(
                        paymentOrder.getAmount(),
                        paymentOrder.getOrderNumber(),
                        "快递订单支付",
                        String.format("从%s到%s的快递服务",
                                mailOrder.getPickupAddress(),
                                mailOrder.getDeliveryAddress())
                );

                // 返回同时包含表单和链接的响应对象
                return new PaymentResponse(
                        paymentOrder.getOrderNumber(),
                        payForm,
                        payUrl,
                        paymentOrder.getAmount(),
                        paymentOrder.getExpireTime()
                );
            } catch (AlipayApiException e) {
                logger.error("生成支付信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成支付信息失败: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("创建支付订单失败，订单号: {}, 错误: {}", mailOrder.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("创建支付订单失败: " + e.getMessage());
        } finally {
            // 释放分布式锁
            distributedLockHelper.unlock(lockKey);
        }
    }

    /**
     * 创建商品订单的支付订单
     * 使用新的订单类型和订单信息字段
     */
    @Transactional
    public PaymentResponse createShoppingOrderPayment(ShoppingOrder shoppingOrder) {
        String orderIdentifier = shoppingOrder.getOrderNumber().toString();
        String lockKey = DistributedLockHelper.getPaymentLockKey(orderIdentifier);

        try {
            // 使用分布式锁确保并发安全，防止重复提交
            if (!distributedLockHelper.tryLock(lockKey)) {
                logger.warn("订单正在处理中，请勿重复提交，订单号: {}", shoppingOrder.getOrderNumber());
                throw new RuntimeException("订单正在处理中，请勿重复提交");
            }

            // 检查是否存在未完成的有效支付订单
            Optional<PaymentOrder> existingOrder = findValidPaymentOrder(OrderType.SHOPPING_ORDER, orderIdentifier);
            if (existingOrder.isPresent()) {
                logger.info("订单已存在有效的支付订单，订单号: {}", shoppingOrder.getOrderNumber());

                try {
                    // 为已存在的订单生成支付表单
                    String payForm = createShoppingOrderPayForm(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            shoppingOrder
                    );

                    // 为已存在的订单生成支付链接
                    String payUrl = createPaymentUrl(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            "商品订单支付",
                            String.format("商品：%s，数量：%d",
                                    shoppingOrder.getProduct().getName(),
                                    shoppingOrder.getQuantity())
                    );

                    return new PaymentResponse(
                            existingOrder.get().getOrderNumber(),
                            payForm,
                            payUrl,
                            existingOrder.get().getAmount(),
                            existingOrder.get().getExpireTime()
                    );
                } catch (AlipayApiException e) {
                    logger.error("生成支付信息失败: {}", e.getMessage(), e);
                    throw new RuntimeException("生成支付信息失败: " + e.getMessage());
                }
            }

            // 创建新的支付订单记录
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setOrderNumber(generatePaymentOrderNumber());
            paymentOrder.setUser(shoppingOrder.getUser());
            paymentOrder.setAmount(shoppingOrder.getTotalAmount());
            paymentOrder.setStatus(PaymentStatus.WAITING);

            // 设置新字段
            paymentOrder.setOrderType(OrderType.SHOPPING_ORDER);
            paymentOrder.setOrderInfo(orderIdentifier);

            // 设置订单时间信息
            LocalDateTime now = LocalDateTime.now();
            paymentOrder.setCreatedTime(now);
            paymentOrder.setExpireTime(now.plusMinutes(30));

            // 保存支付订单到数据库
            PaymentOrder savedPaymentOrder = paymentOrderRepository.save(paymentOrder);

            // 设置支付超时监控
            paymentTimeoutService.setPaymentTimeout(savedPaymentOrder.getOrderNumber());

            // 生成商品订单的描述信息
            StringBuilder orderDescription = new StringBuilder();
            orderDescription.append(String.format("商品：%s", shoppingOrder.getProduct().getName()));
            orderDescription.append(String.format("，数量：%d", shoppingOrder.getQuantity()));

            if (shoppingOrder.getProduct().getWeight() != null) {
                orderDescription.append(String.format("，重量：%.2fkg",
                        shoppingOrder.getProduct().getWeight() * shoppingOrder.getQuantity()));
            }

            orderDescription.append(String.format("，配送方式：%s", shoppingOrder.getDeliveryType().getLabel()));

            if (shoppingOrder.getDeliveryFee() != null) {
                orderDescription.append(String.format("，配送费：%.2f元", shoppingOrder.getDeliveryFee()));
            }

            try {
                // 创建支付宝支付表单
                String payForm = createShoppingOrderPayForm(
                        savedPaymentOrder.getAmount(),
                        savedPaymentOrder.getOrderNumber(),
                        shoppingOrder
                );

                // 创建支付宝支付链接
                String payUrl = createPaymentUrl(
                        savedPaymentOrder.getAmount(),
                        savedPaymentOrder.getOrderNumber(),
                        String.format("%s - %s", shoppingOrder.getStore().getStoreName(), shoppingOrder.getProduct().getName()),
                        orderDescription.toString()
                );

                // 创建支付响应对象
                PaymentResponse response = new PaymentResponse(
                        savedPaymentOrder.getOrderNumber(),
                        payForm,
                        payUrl,
                        savedPaymentOrder.getAmount(),
                        savedPaymentOrder.getExpireTime()
                );

                logger.info("商品订单支付创建成功，订单号: {}, 支付订单号: {}, 支付金额: {}",
                        shoppingOrder.getOrderNumber(),
                        savedPaymentOrder.getOrderNumber(),
                        savedPaymentOrder.getAmount());

                return response;
            } catch (AlipayApiException e) {
                logger.error("生成支付信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("生成支付信息失败: " + e.getMessage());
            }

        } catch (Exception e) {
            // 记录详细的错误信息
            logger.error("创建商品订单支付失败，订单号: {}, 错误: {}",
                    shoppingOrder.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("创建商品订单支付失败: " + e.getMessage());
        } finally {
            // 确保在任何情况下都释放分布式锁
            distributedLockHelper.unlock(lockKey);
        }
    }

    /**
     * 创建代购需求的支付订单
     * 使用新的订单类型和订单信息字段
     */
    @Transactional
    public PaymentResponse createPurchaseRequestPayment(PurchaseRequest request) {
        String orderIdentifier = request.getRequestNumber().toString();
        String lockKey = DistributedLockHelper.getPaymentLockKey(orderIdentifier);

        try {
            // 使用分布式锁确保并发安全
            if (!distributedLockHelper.tryLock(lockKey)) {
                logger.warn("订单正在处理中，请勿重复提交，订单号: {}", request.getRequestNumber());
                throw new RuntimeException("订单正在处理中，请勿重复提交");
            }

            // 检查是否存在未完成的支付订单
            Optional<PaymentOrder> existingOrder = findValidPaymentOrder(OrderType.PURCHASE_REQUEST, orderIdentifier);
            if (existingOrder.isPresent()) {
                logger.info("订单已存在有效的支付订单，订单号: {}", request.getRequestNumber());

                try {
                    // 准备订单描述信息
                    String orderDescription = formatOrderDescription(request);

                    // 为已存在的订单生成支付表单和支付链接
                    String payForm = createAlipayPayForm(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            orderDescription
                    );

                    String payUrl = createPaymentUrl(
                            existingOrder.get().getAmount(),
                            existingOrder.get().getOrderNumber(),
                            "代购需求支付",
                            orderDescription
                    );

                    return new PaymentResponse(
                            existingOrder.get().getOrderNumber(),
                            payForm,
                            payUrl,
                            existingOrder.get().getAmount(),
                            existingOrder.get().getExpireTime()
                    );
                } catch (AlipayApiException e) {
                    logger.error("生成支付信息失败: {}", e.getMessage(), e);
                    throw new RuntimeException("生成支付信息失败: " + e.getMessage());
                }
            }

            // 创建新的支付订单
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setOrderNumber(generatePaymentOrderNumber());
            paymentOrder.setUser(request.getUser());
            paymentOrder.setAmount(request.getTotalAmount());
            paymentOrder.setStatus(PaymentStatus.WAITING);

            // 设置新字段
            paymentOrder.setOrderType(OrderType.PURCHASE_REQUEST);
            paymentOrder.setOrderInfo(orderIdentifier);

            // 设置订单时间信息
            LocalDateTime now = LocalDateTime.now();
            paymentOrder.setCreatedTime(now);
            paymentOrder.setExpireTime(now.plusMinutes(30));

            // 保存支付订单到数据库
            PaymentOrder savedPaymentOrder = paymentOrderRepository.save(paymentOrder);

            // 设置支付超时监控
            paymentTimeoutService.setPaymentTimeout(savedPaymentOrder.getOrderNumber());

            // 准备订单描述信息
            String orderDescription = formatOrderDescription(request);

            try {
                // 创建支付表单HTML
                String payForm = createAlipayPayForm(
                        request.getTotalAmount(),
                        savedPaymentOrder.getOrderNumber(),
                        orderDescription
                );

                // 创建支付链接
                String payUrl = createPaymentUrl(
                        request.getTotalAmount(),
                        savedPaymentOrder.getOrderNumber(),
                        "代购需求支付",
                        orderDescription
                );

                // 生成支付响应，同时包含表单和链接
                return new PaymentResponse(
                        savedPaymentOrder.getOrderNumber(),
                        payForm,
                        payUrl,
                        request.getTotalAmount(),
                        savedPaymentOrder.getExpireTime()
                );
            } catch (AlipayApiException e) {
                logger.error("创建支付信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("创建支付信息失败: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("创建代购需求支付失败，需求编号: {}, 错误: {}", request.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("创建代购需求支付失败: " + e.getMessage());
        } finally {
            distributedLockHelper.unlock(lockKey);
        }
    }

    /**
     * 查找有效的支付订单
     * 使用新的订单类型和订单信息字段
     */
    private Optional<PaymentOrder> findValidPaymentOrder(OrderType orderType, String orderInfo) {
        LocalDateTime now = LocalDateTime.now();

        // 使用新的查询方法
        List<PaymentOrder> existingOrders = paymentOrderRepository.findValidPaymentsByOrderInfo(
                orderType, orderInfo, now);

        return existingOrders.stream().findFirst();
    }

    /**
     * 处理支付成功回调
     * 根据订单类型处理不同类型订单的支付成功逻辑
     */
    @Transactional
    public void handlePaymentSuccess(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");

        logger.info("处理支付宝回调通知: 商户订单号={}, 支付宝交易号={}, 交易状态={}",
                outTradeNo, tradeNo, tradeStatus);

        try {
            // 1. 签名验证
            if (!verifyNotify(params)) {
                logger.error("支付宝回调签名验证失败，终止处理");
                return;
            }
            logger.info("支付宝回调签名验证成功，继续处理");

            // 2. 校验交易状态
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                logger.warn("未知的交易状态: {}, 终止处理", tradeStatus);
                return;
            }

            // 3. 查询订单
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(outTradeNo)
                    .orElseThrow(() -> {
                        logger.error("支付订单不存在: {}", outTradeNo);
                        return new RuntimeException("支付订单不存在");
                    });

            // 4. 幂等性检查
            if (paymentOrder.getStatus() == PaymentStatus.PAID) {
                logger.info("订单已处理过支付成功状态，订单号: {}", outTradeNo);
                return;
            }

            // 5. 状态检查
            if (paymentOrder.getStatus() != PaymentStatus.WAITING) {
                logger.warn("订单状态异常，当前状态: {}, 终止处理", paymentOrder.getStatus());
                return;
            }

            // 6. 更新订单状态
            paymentOrder.setStatus(PaymentStatus.PAID);
            paymentOrder.setPaymentTime(LocalDateTime.now());
            paymentOrder.setAlipayTradeNo(tradeNo);
            paymentOrderRepository.save(paymentOrder);
            logger.info("订单状态已更新为已支付: {}", outTradeNo);

            // 7. 根据订单类型处理后续业务逻辑
            processOrderByType(paymentOrder);

            // 8. 发送通知
            try {
                messageService.sendMessage(
                        paymentOrder.getUser(),
                        String.format("订单支付成功，订单号: %s", paymentOrder.getOrderNumber()),
                        MessageType.ORDER_PAYMENT_SUCCESS,
                        null
                );
                logger.info("支付成功通知已发送: {}", outTradeNo);
            } catch (Exception e) {
                logger.warn("发送支付成功通知失败: {}", e.getMessage());
                // 通知发送失败不影响主流程
            }

            logger.info("支付成功回调处理完成，订单号: {}", outTradeNo);

        } catch (Exception e) {
            logger.error("处理支付回调失败: {} - {}", outTradeNo, e.getMessage(), e);
            throw new RuntimeException("处理支付回调失败", e);
        }
    }

    /**
     * 根据订单类型处理订单支付成功后的业务逻辑
     */
    private void processOrderByType(PaymentOrder paymentOrder) {
        // 获取订单类型和订单信息
        OrderType orderType = paymentOrder.getOrderType();
        String orderInfo = paymentOrder.getOrderInfo();

        if (orderInfo == null || orderInfo.isEmpty()) {
            logger.error("订单信息为空，无法处理: {}", paymentOrder.getOrderNumber());
            return;
        }

        try {
            // 根据订单类型处理不同业务逻辑
            switch (orderType) {
                case MAIL_ORDER:
                    processMailOrderPaymentSuccess(orderInfo);
                    break;

                case SHOPPING_ORDER:
                    processShoppingOrderPaymentSuccess(orderInfo);
                    break;

                case PURCHASE_REQUEST:
                    processPurchaseRequestPaymentSuccess(orderInfo);
                    break;

                default:
                    logger.warn("未知的订单类型: {}, 订单号: {}", orderType, paymentOrder.getOrderNumber());
                    break;
            }
        } catch (Exception e) {
            logger.error("处理订单业务逻辑失败，订单类型: {}, 订单信息: {}, 错误: {}",
                    orderType, orderInfo, e.getMessage(), e);
            throw new RuntimeException("处理订单业务逻辑失败", e);
        }
    }

    /**
     * 处理邮件订单支付成功的业务逻辑
     */
    private void processMailOrderPaymentSuccess(String orderInfo) {
        try {
            UUID mailOrderNumber = UUID.fromString(orderInfo);
            // 调用邮件订单服务处理支付成功后的业务逻辑
            mailOrderService.processAfterPaymentSuccess(mailOrderNumber);
            logger.info("邮件订单支付成功处理完成: {}", orderInfo);
        } catch (Exception e) {
            logger.error("处理邮件订单支付成功失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理邮件订单支付成功失败", e);
        }
    }

    /**
     * 处理商品订单支付成功的业务逻辑
     */
    private void processShoppingOrderPaymentSuccess(String orderInfo) {
        try {
            UUID shoppingOrderNumber = UUID.fromString(orderInfo);
            // 调用商品订单服务处理支付成功后的业务逻辑
            shoppingOrderService.processAfterPaymentSuccess(shoppingOrderNumber);
            logger.info("商品订单支付成功处理完成: {}", orderInfo);
        } catch (Exception e) {
            logger.error("处理商品订单支付成功失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理商品订单支付成功失败", e);
        }
    }

    /**
     * 处理代购需求支付成功的业务逻辑
     */
    private void processPurchaseRequestPaymentSuccess(String orderInfo) {
        try {
            UUID purchaseRequestNumber = UUID.fromString(orderInfo);
            // 调用代购需求服务处理支付成功后的业务逻辑
            purchaseRequestService.handlePaymentSuccess(purchaseRequestNumber);
            logger.info("代购需求支付成功处理完成: {}", orderInfo);
        } catch (Exception e) {
            logger.error("处理代购需求支付成功失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理代购需求支付成功失败", e);
        }
    }

    /**
     * 统一查询支付状态
     * 使用新的订单类型和订单信息字段
     */
    public PaymentStatusResponse queryUnifiedPaymentStatus(String orderNumber) {
        logger.info("开始统一查询订单状态: {}", orderNumber);

        try {
            // 1. 首先检查本地订单状态
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("支付订单不存在"));

            // 如果本地订单已经是终态，直接返回结果
            if (paymentOrder.getStatus() == PaymentStatus.PAID ||
                    paymentOrder.getStatus() == PaymentStatus.REFUNDED ||
                    paymentOrder.getStatus() == PaymentStatus.CANCELLED) {

                return new PaymentStatusResponse(
                        PaymentStatusResponse.Status.SUCCESS,
                        paymentOrder.getStatus().name(),
                        null
                );
            }

            // 2. 构建支付宝查询请求
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_trade_no", orderNumber);
            request.setBizContent(bizContent.toString());

            // 3. 执行查询
            AlipayTradeQueryResponse response = alipayClient.execute(request);

            // 4. 处理查询结果
            if (response.isSuccess()) {
                // 获取交易状态
                String tradeStatus = response.getTradeStatus();

                // 更新本地订单状态
                updateLocalOrderStatus(paymentOrder, tradeStatus, response.getTradeNo());

                // 返回查询结果
                return new PaymentStatusResponse(
                        PaymentStatusResponse.Status.SUCCESS,
                        tradeStatus,
                        null
                );
            } else {
                // 处理特定错误码
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    // 检查订单是否已过期
                    if (paymentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
                        return new PaymentStatusResponse(
                                PaymentStatusResponse.Status.ERROR,
                                null,
                                "订单已超时，请重新创建订单"
                        );
                    }

                    // 订单未支付，可以继续使用原支付表单
                    return new PaymentStatusResponse(
                            PaymentStatusResponse.Status.CREATING,
                            null,
                            "等待支付"
                    );
                }

                // 其他错误情况
                return new PaymentStatusResponse(
                        PaymentStatusResponse.Status.ERROR,
                        null,
                        response.getSubMsg() != null ? response.getSubMsg() : response.getMsg()
                );
            }
        } catch (Exception e) {
            logger.error("统一查询支付状态失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询支付状态失败", e);
        }
    }

    /**
     * 更新本地订单状态
     * 根据支付宝返回的交易状态更新本地订单记录
     */
    private void updateLocalOrderStatus(PaymentOrder paymentOrder, String tradeStatus, String alipayTradeNo) {
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                if (paymentOrder.getStatus() != PaymentStatus.PAID) {
                    paymentOrder.setStatus(PaymentStatus.PAID);
                    paymentOrder.setPaymentTime(LocalDateTime.now());
                    paymentOrder.setAlipayTradeNo(alipayTradeNo);
                    paymentOrderRepository.save(paymentOrder);

                    // 处理订单支付成功业务逻辑
                    processOrderByType(paymentOrder);

                    // 发送支付成功通知
                    messageService.sendMessage(
                            paymentOrder.getUser(),
                            String.format("订单支付成功，订单号: %s", paymentOrder.getOrderNumber()),
                            MessageType.ORDER_PAYMENT_SUCCESS,
                            null
                    );
                }
                break;

            case "TRADE_CLOSED":
                if (paymentOrder.getStatus() == PaymentStatus.WAITING) {
                    paymentOrder.setStatus(PaymentStatus.CANCELLED);
                    paymentOrderRepository.save(paymentOrder);

                    // 发送订单关闭通知
                    messageService.sendMessage(
                            paymentOrder.getUser(),
                            String.format("订单已关闭，订单号: %s", paymentOrder.getOrderNumber()),
                            MessageType.ORDER_PAYMENT_CANCELLED,
                            null
                    );
                }
                break;
        }
    }

    /**
     * 获取用户待支付订单
     * 使用新的订单类型和订单信息字段
     */
    public List<PaymentOrder> getUserPendingPayments(User user) {
        LocalDateTime now = LocalDateTime.now();

        // 获取用户的所有待支付订单
        List<PaymentOrder> allPendingPayments = paymentOrderRepository.findByUserAndStatus(
                user, PaymentStatus.WAITING
        );

        // 过滤掉已过期的订单
        return allPendingPayments.stream()
                .filter(payment -> payment.getExpireTime().isAfter(now))
                .toList();
    }

    /**
     * 创建订单支付表单
     */
    public String createOrderPayment(BigDecimal amount, String orderId, MailOrder order) throws AlipayApiException {
        logger.info("创建订单支付，订单号: {}, 金额: {}", orderId, amount);

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(orderId);
        model.setTotalAmount(amount.toString());
        model.setSubject("快递订单支付");
        model.setBody(String.format("从%s到%s的快递服务",
                order.getPickupAddress(), order.getDeliveryAddress()));
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);

        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
        if (!response.isSuccess()) {
            throw new AlipayApiException("创建支付订单失败: " + response.getMsg());
        }

        logger.info("支付订单创建成功: {}", orderId);
        return response.getBody();
    }

    /**
     * 验证支付宝通知签名
     */
    public boolean verifyNotify(Map<String, String> params) throws AlipayApiException {
        logger.info("开始验证支付宝通知签名，参数数量: {}", params.size());

        // 检查必要参数
        if (!params.containsKey("sign")) {
            logger.error("通知参数中缺少签名字段(sign)");
            return false;
        }

        try {
            // 获取支付宝公钥
            String publicKey = alipayConfig.getPublicKey();

            // 记录关键信息用于调试
            logger.info("使用的支付宝公钥(前缀): {}",
                    publicKey.length() > 20 ? publicKey.substring(0, 20) + "..." : publicKey);

            // 获取签名类型和字符集
            String signType = params.getOrDefault("sign_type", "RSA2");
            String charset = params.getOrDefault("charset", "UTF-8");
            logger.info("签名类型: {}, 字符集: {}", signType, charset);

            // 记录签名值长度
            String sign = params.get("sign");
            logger.info("签名值长度: {}", sign.length());

            // 创建新的参数Map，避免原始Map被修改
            Map<String, String> paramsForCheck = new HashMap<>(params);

            // 执行验签 - 确保使用复制的Map
            boolean result = AlipaySignature.rsaCheckV1(
                    paramsForCheck,  // 使用复制的Map
                    publicKey,
                    charset,
                    signType
            );

            if (result) {
                logger.info("支付宝通知签名验证成功");
            } else {
                logger.error("支付宝通知签名验证失败");

                // 打印部分签名内容用于调试(只显示前20个字符)
                if (sign.length() > 20) {
                    logger.info("签名前20个字符: {}", sign.substring(0, 20));
                }
            }

            return result;
        } catch (Exception e) {
            logger.error("验证支付宝通知签名时发生异常: {}", e.getMessage(), e);
            throw new AlipayApiException("验证签名异常: " + e.getMessage(), e);
        }
    }

    /**
     * 创建商品订单支付表单
     */
    private String createShoppingOrderPayForm(BigDecimal amount, String orderId, ShoppingOrder order) throws AlipayApiException {
        logger.info("创建商品订单支付，订单号: {}, 金额: {}", orderId, amount);

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        // 创建支付宝交易模型
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();

        // 设置基本交易信息
        model.setOutTradeNo(orderId);
        model.setTotalAmount(amount.toString());

        // 构建订单标题：店铺名称 - 商品名称
        String orderTitle = String.format("%s - %s",
                order.getStore().getStoreName(),
                order.getProduct().getName()
        );
        model.setSubject(orderTitle);

        // 构建详细的订单描述
        StringBuilder orderDescription = new StringBuilder();
        orderDescription.append(String.format("商品：%s", order.getProduct().getName()));
        orderDescription.append(String.format("，数量：%d", order.getQuantity()));

        // 添加商品规格信息（如果有）
        if (order.getProduct().getWeight() != null) {
            orderDescription.append(String.format("，重量：%.2fkg", order.getProduct().getWeight() * order.getQuantity()));
        }
        if (order.getProductVolume() != null) {
            orderDescription.append(String.format("，体积：%.3fm³", order.getProductVolume()));
        }

        // 添加配送信息
        orderDescription.append(String.format("，配送方式：%s", order.getDeliveryType().getLabel()));
        if (order.getDeliveryFee() != null) {
            orderDescription.append(String.format("，配送费：%.2f元", order.getDeliveryFee()));
        }

        model.setBody(orderDescription.toString());

        // 设置商品代码（支付宝必需参数）
        model.setProductCode("FAST_INSTANT_TRADE_PAY");

        request.setBizModel(model);

        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
        if (!response.isSuccess()) {
            throw new AlipayApiException("创建支付订单失败: " + response.getMsg());
        }

        logger.info("支付订单创建成功: {}", orderId);
        return response.getBody();
    }

    /**
     * 创建支付宝支付表单
     */
    private String createAlipayPayForm(BigDecimal amount, String orderId, String description) throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(orderId);
        model.setTotalAmount(amount.toString());
        model.setSubject("代购需求支付");
        model.setBody(description);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);

        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
        if (!response.isSuccess()) {
            throw new AlipayApiException("创建支付订单失败: " + response.getMsg());
        }

        return response.getBody();
    }

    /**
     * 格式化订单描述信息
     */
    private String formatOrderDescription(PurchaseRequest request) {
        return String.format("代购需求：%s（商品价格：%.2f元，配送费：%.2f元）",
                request.getTitle(),
                request.getExpectedPrice().doubleValue(),
                request.getDeliveryFee().doubleValue());
    }

    /**
     * 创建支付链接
     */
    public String createPaymentUrl(BigDecimal amount, String orderId, String subject, String description) throws AlipayApiException {
        logger.info("创建订单支付链接，订单号: {}, 金额: {}", orderId, amount);

        // 创建支付宝请求
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        // 构建业务参数
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(orderId);
        model.setTotalAmount(amount.toString());
        model.setSubject(subject);
        model.setBody(description);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);

        // 使用sdkExecute获取支付参数，而不是pageExecute获取表单
        // sdkExecute不会返回完整的HTML表单，而是返回请求参数字符串
        AlipayTradePagePayResponse response = alipayClient.sdkExecute(request);
        if (!response.isSuccess()) {
            throw new AlipayApiException("创建支付链接失败: " + response.getMsg());
        }

        // 构建完整的支付链接 - 使用配置文件中的gatewayUrl
        String payUrl = alipayConfig.getGatewayUrl() + "?" + response.getBody();

        logger.info("支付链接创建成功: {}", orderId);
        return payUrl;
    }

    /**
     * 关闭未支付的交易
     * 适用于：用户主动取消订单，或订单超时需要关闭的场景
     */
    @Transactional
    public void closeUnpaidOrder(String orderNumber) throws AlipayApiException {
        logger.info("开始关闭未支付订单，商户订单号: {}", orderNumber);

        try {
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("支付订单不存在"));

            if (paymentOrder.getStatus() != PaymentStatus.WAITING) {
                throw new RuntimeException("只能关闭待支付状态的订单，当前状态: " + paymentOrder.getStatus());
            }

            // 查询支付宝订单状态
            AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
            JSONObject queryBizContent = new JSONObject();
            queryBizContent.put("out_trade_no", orderNumber);
            queryRequest.setBizContent(queryBizContent.toString());

            AlipayTradeQueryResponse queryResponse = alipayClient.execute(queryRequest);

            if (queryResponse.isSuccess()) {
                String tradeStatus = queryResponse.getTradeStatus();

                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    throw new RuntimeException("订单已支付，无法关闭");
                }

                if ("WAIT_BUYER_PAY".equals(tradeStatus) || tradeStatus == null) {
                    AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
                    JSONObject closeBizContent = new JSONObject();
                    closeBizContent.put("out_trade_no", orderNumber);
                    closeRequest.setBizContent(closeBizContent.toString());

                    AlipayTradeCloseResponse closeResponse = alipayClient.execute(closeRequest);

                    if (closeResponse.isSuccess()) {
                        paymentOrder.setStatus(PaymentStatus.CANCELLED);
                        paymentOrderRepository.save(paymentOrder);

                        messageService.sendMessage(
                                paymentOrder.getUser(),
                                String.format("订单 #%s 已关闭", paymentOrder.getOrderInfo()),
                                MessageType.ORDER_PAYMENT_CANCELLED,
                                null
                        );

                        logger.info("订单关闭成功: {}", orderNumber);
                    } else {
                        throw new AlipayApiException("关闭订单失败: " + closeResponse.getSubMsg());
                    }
                }
            } else if ("ACQ.TRADE_NOT_EXIST".equals(queryResponse.getSubCode())) {
                paymentOrder.setStatus(PaymentStatus.CANCELLED);
                paymentOrderRepository.save(paymentOrder);
                logger.info("订单在支付宝不存在，已更新本地状态: {}", orderNumber);
            } else {
                throw new AlipayApiException("查询订单失败: " + queryResponse.getSubMsg());
            }

        } catch (Exception e) {
            logger.error("关闭订单失败: {}", e.getMessage(), e);
            throw new RuntimeException("关闭订单失败", e);
        }
    }

    /**
     * 提现到支付宝账户
     * 使用支付宝单笔转账接口
     */
    public AlipayWithdrawalResponse withdrawToAlipay(Long userId, BigDecimal amount, String accountInfo,
                                                     String realName, String withdrawalOrderNo) {
        logger.info("开始处理用户 {} 提现请求，订单号: {}, 金额: {}", userId, withdrawalOrderNo, amount);
        AlipayWithdrawalResponse result = new AlipayWithdrawalResponse();
        result.setOutBizNo(withdrawalOrderNo);

        try {
            // 创建转账请求对象
            AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();

            // 设置统一回调地址
            request.setNotifyUrl(alipayConfig.getCallbackUrl());

            // 构建业务参数
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_biz_no", withdrawalOrderNo);
            bizContent.put("trans_amount", amount.toString());
            bizContent.put("product_code", "TRANS_ACCOUNT_NO_PWD");
            bizContent.put("biz_scene", "DIRECT_TRANSFER");
            bizContent.put("order_title", "用户提现");

            // 收款方信息
            JSONObject payeeInfo = new JSONObject();
            payeeInfo.put("identity", accountInfo);
            payeeInfo.put("identity_type", "ALIPAY_USER_ID");
            payeeInfo.put("name", realName);  // 使用真实姓名
            bizContent.put("payee_info", payeeInfo);

            request.setBizContent(bizContent.toString());

            // 配置客户端超时设置
            AlipayConfig clientConfig = new AlipayConfig();
            clientConfig.setConnectTimeout(10000); // 10秒连接超时
            clientConfig.setReadTimeout(20000);    // 20秒读取超时

            // 使用重试机制调用支付宝API
            AlipayFundTransUniTransferResponse response = null;
            int maxRetries = 2;
            int currentRetry = 0;
            Exception lastException = null;

            while (currentRetry <= maxRetries) {
                try {
                    // 调用支付宝API
                    response = alipayClient.execute(request);
                    lastException = null;
                    break;
                } catch (Exception e) {
                    lastException = e;
                    currentRetry++;

                    if (isNetworkTimeoutError(e)) {
                        logger.warn("支付宝API调用超时，尝试第{}次重试", currentRetry);
                        try {
                            Thread.sleep(2000L * currentRetry); // 递增等待时间
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        break;
                    }
                }
            }

            // 处理API响应或异常
            if (response != null && response.isSuccess()) {
                result.setSuccess(true);
                result.setOrderId(response.getOrderId());
                result.setStatus(response.getStatus());
                logger.info("用户 {} 提现请求处理成功，支付宝订单号: {}", userId, response.getOrderId());
            } else {
                result.setSuccess(false);
                String errorMsg = getErrorMessage(lastException, response);
                result.setErrorMessage(errorMsg);
                logger.error("用户 {} 提现请求处理失败: {}", userId, errorMsg);
            }
        } catch (Exception e) {
            logger.error("提现到支付宝处理异常", e);
            result.setSuccess(false);
            result.setErrorMessage("提现处理异常: " + extractErrorMessage(e));
        }

        return result;
    }

    /**
     * 判断是否为网络超时错误
     */
    private boolean isNetworkTimeoutError(Exception e) {
        if (e == null) return false;

        // 检查异常类型和消息内容
        String errorMsg = e.toString();
        return errorMsg.contains("timeout") ||
                errorMsg.contains("timed out") ||
                errorMsg.contains("504") ||
                (e.getCause() != null && e.getCause() instanceof java.net.SocketTimeoutException);
    }

    /**
     * 提取有用的错误信息，避免保存整个HTML响应
     */
    private String extractErrorMessage(Exception e) {
        if (e == null) return "未知错误";

        String fullMessage = e.toString();

        // 检查是否包含HTTP错误状态码
        if (fullMessage.contains("504 Gateway Time-out")) {
            return "支付宝网关超时(504)，请稍后重试";
        } else if (fullMessage.contains("502 Bad Gateway")) {
            return "支付宝网关错误(502)，请稍后重试";
        } else if (fullMessage.contains("500 Internal Server Error")) {
            return "支付宝服务器错误(500)，请稍后重试";
        }

        // 提取有用的错误信息，避免整个HTML响应
        if (e instanceof AlipayApiException apiEx) {
            return "支付宝API错误: " + apiEx.getErrMsg();
        }

        // 截断过长的错误信息
        String message = e.getMessage();
        if (message != null && message.length() > 200) {
            message = message.substring(0, 200) + "...";
        }

        return message;
    }

    /**
     * 从API响应或异常中获取错误信息
     */
    private String getErrorMessage(Exception e, AlipayFundTransUniTransferResponse response) {
        if (response != null && !response.isSuccess()) {
            return "错误码: " + response.getCode() + ", 错误信息: " + response.getSubMsg();
        }

        return extractErrorMessage(e);
    }

    /**
     * 查询提现订单状态
     */
    public AlipayWithdrawalResponse queryWithdrawalStatus(String outBizNo) {
        logger.info("查询提现订单状态: {}", outBizNo);

        try {
            AlipayFundTransOrderQueryRequest request = new AlipayFundTransOrderQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("out_biz_no", outBizNo);
            request.setBizContent(bizContent.toString());

            AlipayFundTransOrderQueryResponse response = alipayClient.execute(request);

            AlipayWithdrawalResponse result = new AlipayWithdrawalResponse();
            result.setOutBizNo(outBizNo);

            if (response.isSuccess()) {
                result.setSuccess(true);
                result.setOrderId(response.getOrderId());
                result.setStatus(response.getStatus());
            } else {
                result.setSuccess(false);
                result.setErrorMessage(response.getSubMsg());
            }

            return result;

        } catch (Exception e) {
            logger.error("查询提现订单状态异常: {}", e.getMessage(), e);
            AlipayWithdrawalResponse result = new AlipayWithdrawalResponse();
            result.setSuccess(false);
            result.setErrorMessage("查询异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 处理支付宝提现成功消息
     */
    @Transactional
    public void handleWithdrawalSuccess(Map<String, String> params) {
        String outBizNo = params.get("out_biz_no");
        String orderId = params.get("order_id");

        logger.info("处理提现成功通知: 订单号={}, 支付宝转账单号={}", outBizNo, orderId);

        try {
            // 查找提现订单
            Optional<WithdrawalOrder> orderOpt = withdrawalOrderRepository.findByOrderNumber(outBizNo);
            if (orderOpt.isEmpty()) {
                logger.warn("未找到提现订单: {}", outBizNo);
                return;
            }

            WithdrawalOrder order = orderOpt.get();

            // 幂等性检查
            if (order.getStatus() == WithdrawalOrder.WithdrawalStatus.SUCCESS) {
                logger.info("提现订单已处理成功，跳过重复处理: {}", outBizNo);
                return;
            }

            // 更新订单状态
            order.setStatus(WithdrawalOrder.WithdrawalStatus.SUCCESS);
            order.setAlipayOrderId(orderId);
            order.setCompletedTime(LocalDateTime.now());

            withdrawalOrderRepository.save(order);

            logger.info("提现成功状态已更新: {}", outBizNo);

            // 发送提现成功通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的提现申请已成功处理，金额: %.2f元", order.getAmount().doubleValue()),
                    MessageType.WALLET_WITHDRAW_SUCCESS,
                    null
            );

        } catch (Exception e) {
            logger.error("处理提现成功通知失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理提现成功通知失败", e);
        }
    }

    /**
     * 处理支付宝提现失败消息
     */
    @Transactional
    public void handleWithdrawalFailure(Map<String, String> params) {
        String outBizNo = params.get("out_biz_no");
        String errorCode = params.get("error_code");

        logger.info("处理提现失败通知: 订单号={}, 错误码={}", outBizNo, errorCode);

        try {
            // 查找提现订单
            Optional<WithdrawalOrder> orderOpt = withdrawalOrderRepository.findByOrderNumber(outBizNo);
            if (orderOpt.isEmpty()) {
                logger.warn("未找到提现订单: {}", outBizNo);
                return;
            }

            WithdrawalOrder order = orderOpt.get();

            // 幂等性检查
            if (order.getStatus() == WithdrawalOrder.WithdrawalStatus.FAILED) {
                logger.info("提现订单已处理失败，跳过重复处理: {}", outBizNo);
                return;
            }

            // 更新订单状态
            order.setStatus(WithdrawalOrder.WithdrawalStatus.FAILED);
            order.setErrorMessage("提现失败: " + errorCode);
            order.setCompletedTime(LocalDateTime.now());

            withdrawalOrderRepository.save(order);

            logger.info("提现失败状态已更新: {}", outBizNo);

            // 添加退款到钱包的逻辑
            walletService.refundFailedWithdrawal(order);

            // 发送提现失败通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的提现申请处理失败，金额: %.2f元已退回钱包", order.getAmount().doubleValue()),
                    MessageType.WALLET_WITHDRAW_FAILED,
                    null
            );

        } catch (Exception e) {
            logger.error("处理提现失败通知失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理提现失败通知失败", e);
        }
    }

    /**
     * 处理订单结算消息
     */
    @Transactional
    public void handleOrderSettle(String orderNumber, String settleAmount) {
        logger.info("处理订单结算消息, 订单号: {}, 结算金额: {}", orderNumber, settleAmount);

        try {
            // 更新订单结算信息，这里可以添加相应的结算记录逻辑
            // 例如更新订单结算状态、记录结算金额等

            logger.info("订单结算处理完成: {}", orderNumber);
        } catch (Exception e) {
            logger.error("处理订单结算消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理订单结算消息失败", e);
        }
    }

    /**
     * 处理退款成功回调
     * 支持多种订单类型的退款处理
     *
     * @param orderNumber 商户订单号
     * @param alipayTradeNo 支付宝交易号
     * @param refundAmount 退款金额
     */
    @Transactional
    public void handleRefundSuccess(String orderNumber, String alipayTradeNo, String refundAmount) {
        logger.info("处理退款成功通知，商户订单号: {}, 支付宝交易号: {}, 退款金额: {}",
                orderNumber, alipayTradeNo, refundAmount);

        try {
            // 1. 查询支付订单
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> {
                        logger.error("未找到支付订单: {}", orderNumber);
                        return new RuntimeException("支付订单不存在");
                    });

            // 2. 检查订单状态
            if (paymentOrder.getStatus() == PaymentStatus.REFUNDED) {
                logger.info("订单已处于退款状态，忽略重复通知: {}", orderNumber);
                return;
            }

            // 3. 更新支付订单状态
            paymentOrder.setStatus(PaymentStatus.REFUNDED);
            paymentOrderRepository.save(paymentOrder);
            logger.info("支付订单状态已更新为已退款: {}", orderNumber);

            // 4. 根据订单类型处理关联订单
            OrderType orderType = paymentOrder.getOrderType();
            String orderInfo = paymentOrder.getOrderInfo();

            if (orderInfo == null || orderInfo.isEmpty()) {
                logger.error("订单信息为空，无法处理关联订单: {}", orderNumber);
                return;
            }

            UUID originalOrderNumber;
            try {
                originalOrderNumber = UUID.fromString(orderInfo);
            } catch (IllegalArgumentException e) {
                logger.error("订单信息格式无效，无法转换为UUID: {}", orderInfo);
                return;
            }

            // 5. 处理不同类型订单的退款业务逻辑
            processRefundByOrderType(orderType, originalOrderNumber, refundAmount);

            // 6. 发送退款成功通知
            sendRefundSuccessNotification(paymentOrder, refundAmount);

            logger.info("退款成功处理完成: {}", orderNumber);

        } catch (Exception e) {
            logger.error("处理退款成功通知失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理退款成功通知失败", e);
        }
    }

    /**
     * 根据订单类型处理退款业务逻辑
     */
    private void processRefundByOrderType(OrderType orderType, UUID originalOrderNumber, String refundAmount) {
        switch (orderType) {
            case MAIL_ORDER:
                processMailOrderRefund(originalOrderNumber, refundAmount);
                break;

            case SHOPPING_ORDER:
                processShoppingOrderRefund(originalOrderNumber, refundAmount);
                break;

            case PURCHASE_REQUEST:
                processPurchaseRequestRefund(originalOrderNumber, refundAmount);
                break;

            default:
                logger.warn("未支持的订单类型: {}, 订单号: {}", orderType, originalOrderNumber);
        }
    }

    /**
     * 处理邮件订单退款
     */
    private void processMailOrderRefund(UUID orderNumber, String refundAmount) {
        try {
            Optional<MailOrder> mailOrderOpt = mailOrderService.findByOrderNumber(orderNumber);

            if (mailOrderOpt.isPresent()) {
                MailOrder mailOrder = mailOrderOpt.get();

                // 更新订单状态为已退款
                mailOrder.setOrderStatus(OrderStatus.REFUNDED);
                mailOrderService.updateMailOrder(mailOrder);

                logger.info("邮件订单状态已更新为已退款: {}", orderNumber);
            } else {
                logger.warn("未找到对应的邮件订单: {}", orderNumber);
            }
        } catch (Exception e) {
            logger.error("处理邮件订单退款失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理邮件订单退款失败", e);
        }
    }

    /**
     * 处理商品订单退款
     */
    private void processShoppingOrderRefund(UUID orderNumber, String refundAmount) {
        try {
            ShoppingOrder order = shoppingOrderService.getOrderByNumber(orderNumber);

            // 更新订单状态为已退款
            order.setOrderStatus(OrderStatus.REFUNDED);
            order.setRefundStatus("COMPLETED");
            order.setRefundAmount(new BigDecimal(refundAmount));
            order.setRefundTime(LocalDateTime.now());
            shoppingOrderRepository.save(order);

            // 恢复商品库存（如果需要）
            Product product = order.getProduct();
            if (product != null && order.getOrderStatus() != OrderStatus.DELIVERED) {
                // 只有未送达的订单才恢复库存
                product.setStock(product.getStock() + order.getQuantity());
                productRepository.save(product);
                logger.info("已恢复商品库存, 商品ID: {}, 数量: {}", product.getId(), order.getQuantity());
            }

            logger.info("商品订单状态已更新为已退款: {}", orderNumber);
        } catch (Exception e) {
            logger.error("处理商品订单退款失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理商品订单退款失败", e);
        }
    }

    /**
     * 处理代购需求退款
     */
    private void processPurchaseRequestRefund(UUID requestNumber, String refundAmount) {
        try {
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.findByRequestNumber(requestNumber);

            if (requestOpt.isPresent()) {
                PurchaseRequest request = requestOpt.get();

                // 更新需求状态为已退款
                request.setStatus(OrderStatus.REFUNDED);
                purchaseRequestService.updatePurchaseRequest(request);

                logger.info("代购需求状态已更新为已退款: {}", requestNumber);
            } else {
                logger.warn("未找到对应的代购需求: {}", requestNumber);
            }
        } catch (Exception e) {
            logger.error("处理代购需求退款失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理代购需求退款失败", e);
        }
    }

    /**
     * 发送退款成功通知
     */
    private void sendRefundSuccessNotification(PaymentOrder paymentOrder, String refundAmount) {
        try {
            messageService.sendMessage(
                    paymentOrder.getUser(),
                    String.format("您的订单 #%s 退款已成功处理，退款金额: %s元",
                            paymentOrder.getOrderInfo(), refundAmount),
                    MessageType.ORDER_REFUND_SUCCESS,
                    null
            );
            logger.info("退款成功通知已发送, 用户ID: {}", paymentOrder.getUser().getId());
        } catch (Exception e) {
            logger.warn("发送退款成功通知失败: {}", e.getMessage());
            // 通知发送失败不影响主流程
        }
    }
}