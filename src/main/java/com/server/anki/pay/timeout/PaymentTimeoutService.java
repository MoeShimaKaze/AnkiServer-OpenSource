package com.server.anki.pay.timeout;

import com.server.anki.config.RedisConfig;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.service.AbandonedOrderService;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.pay.payment.OrderType;
import com.server.anki.pay.payment.PaymentOrder;
import com.server.anki.pay.payment.PaymentOrderRepository;
import com.server.anki.pay.payment.PaymentStatus;
import com.server.anki.pay.timeout.mq.PaymentTimeoutProducer;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.repository.ProductRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.shopping.service.PurchaseRequestService;
import com.server.anki.shopping.service.ShoppingOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PaymentTimeoutService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private PaymentTimeoutProducer paymentTimeoutProducer;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private AbandonedOrderService abandonedOrderService;

    // 添加对其他订单类型的处理服务
    @Autowired
    private ShoppingOrderService shoppingOrderService;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private ProductRepository productRepository;
    /**
     * 设置支付超时监控
     */
    public void setPaymentTimeout(String orderId) {
        String key = RedisConfig.getPaymentTimeoutKey(orderId);
        Duration timeoutDuration = Duration.ofMinutes(redisConfig.getPaymentTimeoutDuration());

        redisTemplate.opsForValue().set(
                key,
                LocalDateTime.now().toString(),
                timeoutDuration
        );
        log.info("已设置订单 {} 的支付超时时间为 {} 分钟", orderId, timeoutDuration.toMinutes());
    }

    /**
     * 检查并处理超时订单
     * 定时任务保持每分钟执行一次，查找所有已过期的待支付订单
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkTimeoutOrders() {
        List<PaymentOrder> timeoutOrders = paymentOrderRepository
                .findByStatusAndExpireTimeBefore(PaymentStatus.WAITING, LocalDateTime.now());

        for (PaymentOrder order : timeoutOrders) {
            handleTimeoutOrder(order);
        }
    }

    /**
     * 处理超时订单
     */
    private void handleTimeoutOrder(PaymentOrder order) {
        try {
            // 更新支付订单状态为超时
            order.setStatus(PaymentStatus.TIMEOUT);
            paymentOrderRepository.save(order);

            // 处理对应类型的订单
            processOrderTimeout(order);

            // 发送超时通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("订单 #%s 因超时未支付已自动取消", order.getOrderNumber()),
                    MessageType.ORDER_PAYMENT_TIMEOUT,
                    null
            );

            // 发布支付超时事件到消息队列
            publishPaymentTimeoutEvent(order);

            log.info("订单 {} 已标记为超时并完成相关处理", order.getOrderNumber());
        } catch (Exception e) {
            log.error("处理超时订单 {} 时发生错误", order.getOrderNumber(), e);
            throw new RuntimeException("处理超时订单失败", e);
        }
    }

    /**
     * 处理关联订单的超时状态
     * 根据订单类型分派到不同的处理方法
     */
    private void processOrderTimeout(PaymentOrder paymentOrder) {
        try {
            OrderType orderType = paymentOrder.getOrderType();
            String orderInfo = paymentOrder.getOrderInfo();

            log.info("处理关联订单超时, 订单类型: {}, 订单信息: {}", orderType, orderInfo);

            UUID orderNumber;
            try {
                orderNumber = UUID.fromString(orderInfo);
            } catch (IllegalArgumentException e) {
                log.error("订单信息格式无效，无法转换为UUID: {}", orderInfo);
                return;
            }

            switch (orderType) {
                case MAIL_ORDER:
                    processMailOrderTimeout(orderNumber);
                    break;

                case SHOPPING_ORDER:
                    processShoppingOrderTimeout(orderNumber);
                    break;

                case PURCHASE_REQUEST:
                    processPurchaseRequestTimeout(orderNumber);
                    break;

                default:
                    log.warn("未支持的订单类型: {}, 订单号: {}", orderType, paymentOrder.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("处理关联订单超时时发生错误, 订单号: {}", paymentOrder.getOrderNumber(), e);
            throw new RuntimeException("处理关联订单超时失败", e);
        }
    }

    /**
     * 处理邮件订单超时
     */
    private void processMailOrderTimeout(UUID mailOrderNumber) {
        try {
            Optional<MailOrder> mailOrderOpt = mailOrderService.findByOrderNumber(mailOrderNumber);

            if (mailOrderOpt.isPresent()) {
                MailOrder mailOrder = mailOrderOpt.get();

                // 增加状态检查逻辑
                if (mailOrder.getOrderStatus() == OrderStatus.PAYMENT_PENDING) {
                    // 先将订单状态更新为 PAYMENT_TIMEOUT
                    mailOrder.setOrderStatus(OrderStatus.PAYMENT_TIMEOUT);
                    mailOrderService.updateMailOrder(mailOrder);

                    // 然后进行归档
                    abandonedOrderService.archiveAndDeleteOrder(mailOrder);
                    log.info("已归档支付超时的邮件订单: {}", mailOrderNumber);
                } else {
                    log.warn("邮件订单 {} 当前状态为 {}，跳过超时处理",
                            mailOrderNumber, mailOrder.getOrderStatus());
                }
            } else {
                log.warn("未找到对应的邮件订单: {}", mailOrderNumber);
            }
        } catch (Exception e) {
            log.error("处理超时邮件订单时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("处理超时邮件订单失败", e);
        }
    }

    /**
     * 处理商品订单超时
     */
    private void processShoppingOrderTimeout(UUID orderNumber) {
        try {
            ShoppingOrder order = shoppingOrderService.getOrderByNumber(orderNumber);

            if (order.getOrderStatus() == OrderStatus.PAYMENT_PENDING) {
                // 更新订单状态为超时
                order.setOrderStatus(OrderStatus.PAYMENT_TIMEOUT);
                shoppingOrderRepository.save(order);
                log.info("已更新超时的商品订单状态: {}", orderNumber);

                // 恢复商品库存
                Product product = order.getProduct();
                if (product != null) {
                    // 恢复预扣的库存
                    product.setStock(product.getStock() + order.getQuantity());
                    productRepository.save(product);
                    log.info("已恢复商品库存, 商品ID: {}, 数量: {}", product.getId(), order.getQuantity());
                }
            } else {
                log.warn("商品订单 {} 当前状态为 {}，跳过超时处理",
                        orderNumber, order.getOrderStatus());
            }
        } catch (Exception e) {
            log.error("处理超时商品订单时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("处理超时商品订单失败", e);
        }
    }

    /**
     * 处理代购需求超时
     */
    private void processPurchaseRequestTimeout(UUID requestNumber) {
        try {
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.findByRequestNumber(requestNumber);

            if (requestOpt.isPresent()) {
                PurchaseRequest request = requestOpt.get();

                if (request.getStatus() == OrderStatus.PAYMENT_PENDING) {
                    // 更新需求状态为超时
                    request.setStatus(OrderStatus.PAYMENT_TIMEOUT);
                    purchaseRequestService.updatePurchaseRequest(request);
                    log.info("已更新超时的代购需求状态: {}", requestNumber);

                    // 通知发布者
                    messageService.sendMessage(
                            request.getUser(),
                            String.format("您的代购需求 #%s 因支付超时已自动取消", requestNumber),
                            MessageType.ORDER_PAYMENT_TIMEOUT,
                            null
                    );
                } else {
                    log.warn("代购需求 {} 当前状态为 {}，跳过超时处理",
                            requestNumber, request.getStatus());
                }
            } else {
                log.warn("未找到对应的代购需求: {}", requestNumber);
            }
        } catch (Exception e) {
            log.error("处理超时代购需求时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("处理超时代购需求失败", e);
        }
    }

    /**
     * 发布支付超时事件到RabbitMQ
     */
    private void publishPaymentTimeoutEvent(PaymentOrder order) {
        paymentTimeoutProducer.sendTimeoutMessage(order.getOrderNumber());
        log.debug("已发送订单 {} 的支付超时事件到消息队列", order.getOrderNumber());
    }
}