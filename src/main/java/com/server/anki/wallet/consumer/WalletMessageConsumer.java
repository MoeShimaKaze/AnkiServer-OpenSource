package com.server.anki.wallet.consumer;

import com.server.anki.config.RabbitMQConfig;
import com.server.anki.wallet.service.WalletTransactionService;
import com.server.anki.wallet.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 钱包消息消费者
 * 负责处理钱包相关的各类异步消息
 */
@Component
public class WalletMessageConsumer {
    private static final Logger logger = LoggerFactory.getLogger(WalletMessageConsumer.class);

    @Autowired
    private WalletTransactionService transactionService;

    /**
     * 处理钱包初始化消息
     */
    @RabbitListener(queues = RabbitMQConfig.WALLET_INIT_QUEUE)
    public void handleWalletInit(WalletInitMessage message) {
        logger.info("收到钱包初始化消息: messageId={}", message.getMessageId());
        try {
            transactionService.handleWalletInit(message);
        } catch (Exception e) {
            handleMessageError(message, e);
        }
    }

    /**
     * 处理转账消息
     */
    @RabbitListener(queues = RabbitMQConfig.WALLET_TRANSFER_QUEUE)
    public void handleTransfer(TransferMessage message) {
        logger.info("收到转账消息: messageId={}", message.getMessageId());
        try {
            transactionService.handleTransfer(message);
        } catch (Exception e) {
            handleMessageError(message, e);
        }
    }

    /**
     * 处理提现消息
     */
    @RabbitListener(queues = RabbitMQConfig.WALLET_WITHDRAW_QUEUE)
    public void handleWithdrawal(WithdrawalMessage message) {
        logger.info("收到提现消息: messageId={}", message.getMessageId());
        try {
            transactionService.handleWithdrawal(message);
        } catch (Exception e) {
            handleMessageError(message, e);
        }
    }

    /**
     * 处理余额变更消息
     */
    @RabbitListener(queues = RabbitMQConfig.WALLET_BALANCE_QUEUE)
    public void handleBalanceChange(BalanceChangeMessage message) {
        logger.info("收到余额变更消息: messageId={}", message.getMessageId());
        try {
            transactionService.handleBalanceChange(message);
        } catch (Exception e) {
            handleMessageError(message, e);
        }
    }

    /**
     * 统一的消息错误处理方法
     * 实现重试机制和错误日志记录
     */
    private void handleMessageError(BaseWalletMessage message, Exception e) {
        logger.error("处理钱包消息时发生错误: messageId={}, error={}",
                message.getMessageId(), e.getMessage());

        // 检查重试次数
        if (message.getRetryCount() < 3) {
            // 更新重试信息
            WalletMessageUtils.updateRetryInfo(message);

            // 抛出异常使消息重新入队
            throw new AmqpRejectAndDontRequeueException(
                    String.format("消息处理失败,将重试: %s", e.getMessage())
            );
        } else {
            logger.error("消息处理失败且超过重试次数上限，消息将被发送到死信队列: messageId={}",
                    message.getMessageId());
        }
    }
}