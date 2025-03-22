package com.server.anki.pay.timeout.mq;

import com.alipay.api.AlipayApiException;
import com.server.anki.alipay.AlipayService;
import com.server.anki.pay.payment.PaymentOrder;
import com.server.anki.pay.payment.PaymentOrderRepository;
import com.server.anki.pay.payment.PaymentStatus;
import com.server.anki.message.service.MessageService;
import com.server.anki.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付超时消息消费者
 * 负责处理订单支付超时的场景，包括：
 * 1. 关闭支付宝支付订单
 * 2. 更新本地订单状态
 * 3. 发送超时通知
 */
@Component
public class PaymentTimeoutConsumer {
    private static final Logger logger = LoggerFactory.getLogger(PaymentTimeoutConsumer.class);

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private MessageService messageService;

    /**
     * 处理支付超时消息
     * 使用 @RabbitListener 监听支付超时队列
     * 当收到超时消息时，会自动调用此方法处理
     */
    @RabbitListener(queues = "payment.timeout.queue")
    @Transactional
    public void handleTimeoutMessage(PaymentTimeoutMessage message) {
        String orderNumber = message.getOrderNumber();
        logger.info("接收到支付超时消息，订单号: {}", orderNumber);

        try {
            // 查询本地订单状态
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(orderNumber)
                    .orElse(null);

            // 如果订单不存在，记录错误并返回
            if (paymentOrder == null) {
                logger.error("支付订单不存在，订单号: {}", orderNumber);
                return;
            }

            // 只处理待支付状态的订单
            if (paymentOrder.getStatus() != PaymentStatus.WAITING) {
                logger.info("订单状态不是待支付，无需处理超时，订单号: {}, 当前状态: {}",
                        orderNumber, paymentOrder.getStatus());
                return;
            }

            // 调用支付宝接口关闭订单
            try {
                alipayService.closeUnpaidOrder(orderNumber);
                logger.info("支付宝订单关闭成功，订单号: {}", orderNumber);
            } catch (AlipayApiException e) {
                // 如果是订单不存在的错误，可以继续处理
                if (e.getMessage().contains("ACQ.TRADE_NOT_EXIST")) {
                    logger.warn("支付宝订单不存在，继续处理本地订单状态，订单号: {}", orderNumber);
                } else {
                    // 其他错误需要重试
                    throw e;
                }
            }

            // 更新本地订单状态
            paymentOrder.setStatus(PaymentStatus.TIMEOUT);
            paymentOrderRepository.save(paymentOrder);

            // 发送超时通知
            messageService.sendMessage(
                    paymentOrder.getUser(),
                    String.format("订单 #%s 因超时未支付已自动取消", paymentOrder.getOrderNumber()),
                    MessageType.ORDER_PAYMENT_TIMEOUT,
                    null
            );

            logger.info("支付超时处理完成，订单号: {}", orderNumber);

        } catch (Exception e) {
            logger.error("处理支付超时消息失败，订单号: {}", orderNumber, e);
            // 抛出异常使消息重新入队
            throw new RuntimeException("处理支付超时消息失败", e);
        }
    }
}