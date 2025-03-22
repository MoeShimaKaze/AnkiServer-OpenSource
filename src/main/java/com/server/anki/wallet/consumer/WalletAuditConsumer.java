package com.server.anki.wallet.consumer;

import com.server.anki.wallet.entity.WalletAudit;
import com.server.anki.wallet.repository.WalletAuditRepository;
import com.server.anki.wallet.message.WalletAuditMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 钱包审计消息消费者
 * 负责处理审计消息并持久化到数据库
 */
@Component
public class WalletAuditConsumer {
    private static final Logger logger = LoggerFactory.getLogger(WalletAuditConsumer.class);

    @Autowired
    private WalletAuditRepository walletAuditRepository;

    /**
     * 处理审计消息
     */
    @RabbitListener(queues = "wallet.audit.queue")
    @Transactional
    public void handleAuditMessage(WalletAuditMessage message) {
        try {
            logger.debug("Processing audit message: {}", message.getMessageId());

            WalletAudit audit = new WalletAudit();
            audit.setWalletId(message.getWalletId());
            audit.setUserId(message.getUserId());
            audit.setAction(message.getAuditType());
            audit.setAmount(message.getAmount());
            audit.setReason(message.getReason());
            audit.setPerformedBy(message.getPerformedBy());
            audit.setTimestamp(message.getTimestamp());
            audit.setAdditionalInfo(message.getAdditionalInfo());

            walletAuditRepository.save(audit);

            logger.info("Audit message processed successfully: {}", message.getMessageId());
        } catch (Exception e) {
            logger.error("Failed to process audit message: {}, error: {}",
                    message.getMessageId(), e.getMessage());

            if (message.getRetryCount() < 3) {
                message.setRetryCount(message.getRetryCount() + 1);
                message.setLastRetryTime(LocalDateTime.now());
                throw new AmqpRejectAndDontRequeueException("Processing failed, will retry");
            } else {
                // 超过重试次数，消息将进入死信队列
                logger.error("Max retry count exceeded for audit message: {}",
                        message.getMessageId());
            }
        }
    }
}