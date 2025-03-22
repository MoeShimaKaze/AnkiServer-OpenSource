package com.server.anki.wallet.consumer;

import com.server.anki.config.RabbitMQConfig;
import com.server.anki.wallet.message.BaseWalletMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 钱包死信消息消费者
 * 负责处理所有处理失败的钱包消息
 */
@Component
public class WalletDeadLetterConsumer {
    private static final Logger logger = LoggerFactory.getLogger(WalletDeadLetterConsumer.class);

    /**
     * 处理进入死信队列的消息
     * 主要负责记录错误日志，可以扩展告警等功能
     */
    @RabbitListener(queues = RabbitMQConfig.WALLET_DLQ)
    public void handleDeadLetter(BaseWalletMessage message) {
        logger.error("收到钱包死信消息: messageId={}, type={}, retryCount={}",
                message.getMessageId(),
                message.getMessageType(),
                message.getRetryCount()
        );

        // 这里可以添加告警通知逻辑
        // 比如发送邮件、短信等

        // 也可以将失败消息保存到数据库中
        // 方便后续人工介入处理
    }
}