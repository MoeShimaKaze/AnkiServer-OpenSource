package com.server.anki.config;

import com.server.anki.wallet.message.WalletMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 配置所有消息队列的队列、交换机和绑定关系
 */
@Configuration
public class RabbitMQConfig {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    // 原有的通知队列配置
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String MESSAGE_QUEUE = "message.queue";
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String MESSAGE_EXCHANGE = "message.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    public static final String NOTIFICATION_ROUTING_KEY = "notification.routing.key";
    public static final String MESSAGE_ROUTING_KEY = "message.routing.key";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.letter.routing.key";

    // 新增聊天消息队列配置
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String CHAT_DLQ = "chat.dlq";  // 聊天死信队列

    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_DLX = "chat.dlx";  // 聊天死信交换机

    public static final String CHAT_ROUTING_KEY = "chat.routing.key";
    public static final String CHAT_DLK = "chat.dlk";  // 聊天死信路由键

    // 支付超时相关的常量配置
    public static final String PAYMENT_TIMEOUT_QUEUE = "pay.timeout.queue";
    public static final String PAYMENT_TIMEOUT_DLQ = "pay.timeout.dlq";  // 支付超时死信队列
    public static final String PAYMENT_TIMEOUT_EXCHANGE = "payment.timeout.exchange";
    public static final String PAYMENT_TIMEOUT_DLX = "payment.timeout.dlx";  // 支付超时死信交换机
    public static final String PAYMENT_TIMEOUT_ROUTING_KEY = "pay.timeout.key";
    public static final String PAYMENT_TIMEOUT_DLK = "pay.timeout.dlk";  // 支付超时死信路由键

    // 钱包服务相关的常量配置
    public static final String WALLET_EXCHANGE = "wallet.exchange";
    public static final String WALLET_DLX = "wallet.dlx";

    // 钱包初始化队列
    public static final String WALLET_INIT_QUEUE = "wallet.init.queue";
    public static final String WALLET_INIT_KEY = "wallet.init";

    // 余额变更队列
    public static final String WALLET_BALANCE_QUEUE = "wallet.balance.queue";
    public static final String WALLET_BALANCE_KEY = "wallet.balance";

    // 转账处理队列
    public static final String WALLET_TRANSFER_QUEUE = "wallet.transfer.queue";
    public static final String WALLET_TRANSFER_KEY = "wallet.transfer";

    // 提现处理队列
    public static final String WALLET_WITHDRAW_QUEUE = "wallet.withdraw.queue";
    public static final String WALLET_WITHDRAW_KEY = "wallet.withdraw";

    // 钱包死信队列
    public static final String WALLET_DLQ = "wallet.dlq";
    public static final String WALLET_DLK = "wallet.dlk";

    // 审计相关配置
    public static final String AUDIT_QUEUE = "wallet.audit.queue";
    public static final String AUDIT_DLQ = "wallet.audit.dlq";
    public static final String AUDIT_EXCHANGE = "wallet.audit.exchange";
    public static final String AUDIT_DLX = "wallet.audit.dlx";
    public static final String AUDIT_ROUTING_KEY = "wallet.audit";
    public static final String AUDIT_DLK = "wallet.audit.dlk";

    // 通用配置
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                logger.error("消息发送失败: {}", cause);
            }
        });
        return template;
    }

    // 原有的通知队列定义
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue messageQueue() {
        return QueueBuilder.durable(MESSAGE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    // 新增聊天队列定义
    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .withArgument("x-dead-letter-exchange", CHAT_DLX)
                .withArgument("x-dead-letter-routing-key", CHAT_DLK)
                .withArgument("x-message-ttl", 60000) // 消息过期时间：1分钟
                .build();
    }

    @Bean
    public Queue paymentTimeoutQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", PAYMENT_TIMEOUT_DLK)
                .withArgument("x-message-ttl", 60000) // 消息过期时间：1分钟
                .build();
    }

    /**
     * 创建支付超时死信队列
     */
    @Bean
    public Queue paymentTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_DLQ).build();
    }

    /**
     * 创建支付超时交换机
     */
    @Bean
    public DirectExchange paymentTimeoutExchange() {
        return new DirectExchange(PAYMENT_TIMEOUT_EXCHANGE);
    }

    /**
     * 创建支付超时死信交换机
     */
    @Bean
    public DirectExchange paymentTimeoutDeadLetterExchange() {
        return new DirectExchange(PAYMENT_TIMEOUT_DLX);
    }

    /**
     * 绑定支付超时队列到交换机
     */
    @Bean
    public Binding paymentTimeoutBinding() {
        return BindingBuilder.bind(paymentTimeoutQueue())
                .to(paymentTimeoutExchange())
                .with(PAYMENT_TIMEOUT_ROUTING_KEY);
    }

    /**
     * 绑定支付超时死信队列到死信交换机
     */
    @Bean
    public Binding paymentTimeoutDeadLetterBinding() {
        return BindingBuilder.bind(paymentTimeoutDeadLetterQueue())
                .to(paymentTimeoutDeadLetterExchange())
                .with(PAYMENT_TIMEOUT_DLK);
    }


    @Bean
    public Queue chatDeadLetterQueue() {
        return QueueBuilder.durable(CHAT_DLQ).build();
    }

    // 原有的交换机定义
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(MESSAGE_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    // 新增聊天交换机定义
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }

    @Bean
    public DirectExchange chatDeadLetterExchange() {
        return new DirectExchange(CHAT_DLX);
    }

    // 原有的绑定关系定义
    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(notificationExchange)
                .with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding messageBinding(Queue messageQueue, DirectExchange messageExchange) {
        return BindingBuilder
                .bind(messageQueue)
                .to(messageExchange)
                .with(MESSAGE_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    // 新增聊天绑定关系定义
    @Bean
    public Binding chatBinding(Queue chatQueue, DirectExchange chatExchange) {
        return BindingBuilder
                .bind(chatQueue)
                .to(chatExchange)
                .with(CHAT_ROUTING_KEY);
    }

    @Bean
    public Binding chatDeadLetterBinding(Queue chatDeadLetterQueue, DirectExchange chatDeadLetterExchange) {
        return BindingBuilder
                .bind(chatDeadLetterQueue)
                .to(chatDeadLetterExchange)
                .with(CHAT_DLK);
    }

    // 钱包初始化队列
    @Bean
    public Queue walletInitQueue() {
        return QueueBuilder.durable(WALLET_INIT_QUEUE)
                .withArgument("x-dead-letter-exchange", WALLET_DLX)
                .withArgument("x-dead-letter-routing-key", WALLET_DLK)
                .withArgument("x-message-ttl", 300000) // 5分钟过期时间
                .build();
    }

    // 余额变更队列
    @Bean
    public Queue walletBalanceQueue() {
        return QueueBuilder.durable(WALLET_BALANCE_QUEUE)
                .withArgument("x-dead-letter-exchange", WALLET_DLX)
                .withArgument("x-dead-letter-routing-key", WALLET_DLK)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    // 转账处理队列
    @Bean
    public Queue walletTransferQueue() {
        return QueueBuilder.durable(WALLET_TRANSFER_QUEUE)
                .withArgument("x-dead-letter-exchange", WALLET_DLX)
                .withArgument("x-dead-letter-routing-key", WALLET_DLK)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    // 提现处理队列
    @Bean
    public Queue walletWithdrawQueue() {
        return QueueBuilder.durable(WALLET_WITHDRAW_QUEUE)
                .withArgument("x-dead-letter-exchange", WALLET_DLX)
                .withArgument("x-dead-letter-routing-key", WALLET_DLK)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    // 钱包死信队列
    @Bean
    public Queue walletDeadLetterQueue() {
        return QueueBuilder.durable(WALLET_DLQ)
                .build();
    }

    // 钱包服务交换机
    @Bean
    public DirectExchange walletExchange() {
        return new DirectExchange(WALLET_EXCHANGE);
    }

    // 钱包死信交换机
    @Bean
    public DirectExchange walletDeadLetterExchange() {
        return new DirectExchange(WALLET_DLX);
    }

    // 钱包初始化绑定
    @Bean
    public Binding walletInitBinding() {
        return BindingBuilder.bind(walletInitQueue())
                .to(walletExchange())
                .with(WALLET_INIT_KEY);
    }

    // 余额变更绑定
    @Bean
    public Binding walletBalanceBinding() {
        return BindingBuilder.bind(walletBalanceQueue())
                .to(walletExchange())
                .with(WALLET_BALANCE_KEY);
    }

    // 转账处理绑定
    @Bean
    public Binding walletTransferBinding() {
        return BindingBuilder.bind(walletTransferQueue())
                .to(walletExchange())
                .with(WALLET_TRANSFER_KEY);
    }

    // 提现处理绑定
    @Bean
    public Binding walletWithdrawBinding() {
        return BindingBuilder.bind(walletWithdrawQueue())
                .to(walletExchange())
                .with(WALLET_WITHDRAW_KEY);
    }

    // 钱包死信队列绑定
    @Bean
    public Binding walletDeadLetterBinding() {
        return BindingBuilder.bind(walletDeadLetterQueue())
                .to(walletDeadLetterExchange())
                .with(WALLET_DLK);
    }

    // 添加路由键获取工具方法
    public static String getWalletRoutingKey(WalletMessageType messageType) {
        return switch (messageType) {
            case WALLET_INIT -> WALLET_INIT_KEY;
            case BALANCE_CHANGE -> WALLET_BALANCE_KEY;
            case TRANSFER -> WALLET_TRANSFER_KEY;
            case WITHDRAWAL -> WALLET_WITHDRAW_KEY;
            default -> throw new IllegalArgumentException("未知的钱包消息类型: " + messageType);
        };
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_DLX)
                .withArgument("x-dead-letter-routing-key", AUDIT_DLK)
                .build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    public DirectExchange auditExchange() {
        return new DirectExchange(AUDIT_EXCHANGE);
    }

    @Bean
    public DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(AUDIT_DLX);
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue())
                .to(auditExchange())
                .with(AUDIT_ROUTING_KEY);
    }

    @Bean
    public Binding auditDeadLetterBinding() {
        return BindingBuilder.bind(auditDeadLetterQueue())
                .to(auditDeadLetterExchange())
                .with(AUDIT_DLK);
    }
}