package com.server.anki.pay.timeout.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 支付超时消息生产者
 */
@Component
public class PaymentTimeoutProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "payment.exchange";
    private static final String ROUTING_KEY = "pay.timeout";

    public void sendTimeoutMessage(String orderNumber) {
        PaymentTimeoutMessage message = new PaymentTimeoutMessage();
        message.setOrderNumber(orderNumber);
        message.setTimeoutTime(LocalDateTime.now());

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }
}

