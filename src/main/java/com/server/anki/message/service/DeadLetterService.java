package com.server.anki.message.service;

import com.server.anki.message.Message;
import com.server.anki.message.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeadLetterService {
    private static final Logger logger = LoggerFactory.getLogger(DeadLetterService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AlertService alertService;

    @Autowired
    private MessageRepository messageRepository;

    @Transactional
    public void handleDeadLetter(Message message, String originalQueue, String failureReason) {
        if (message.getId() == null) {
            logger.error("收到未持久化的死信消息，无法处理");
            return;
        }

        String messageId = message.getId().toString();
        int retryCount = retryCountMap.getOrDefault(messageId, 0);

        logger.error("处理死信消息 - 消息ID: {}, 原始队列: {}, 失败原因: {}, 重试次数: {}",
                messageId, originalQueue, failureReason, retryCount);

        // 更新消息的重试信息
        message.setRetryCount(retryCount);
        message.setLastRetryTime(LocalDateTime.now());
        message.setFailureReason(failureReason);
        messageRepository.save(message);

        if (retryCount < MAX_RETRY_COUNT) {
            // 增加重试次数
            retryCountMap.put(messageId, retryCount + 1);

            // 重新发送到原始队列，添加延迟
            logger.info("尝试重新发送消息 - 消息ID: {}, 重试次数: {}", messageId, retryCount + 1);
            rabbitTemplate.convertAndSend(originalQueue, message, msg -> {
                // 设置消息延迟，采用指数退避策略
                long delay = (long) (Math.pow(2, retryCount) * 1000);
                // 这里直接传 delay (Long 类型)，而不是传 (int)
                msg.getMessageProperties().setDelayLong(delay);
                return msg;
            });

        } else {
            logger.error("消息重试次数超过最大限制 - 消息ID: {}", messageId);
            // 清理重试计数
            retryCountMap.remove(messageId);

            // 发送告警通知
            alertService.sendAlert(
                    String.format(
                            "消息处理失败 - ID: %s, 队列: %s, 原因: %s, 重试次数: %d",
                            messageId, originalQueue, failureReason, retryCount
                    )
            );

            // 记录最终失败状态
            message.setFailureReason("已达到最大重试次数: " + failureReason);
            messageRepository.save(message);
        }
    }

    // 定期清理过期的重试计数
    @Scheduled(fixedDelay = 3600000) // 每小时执行一次
    public void cleanupRetryCount() {
        logger.info("清理过期的重试计数记录");
        retryCountMap.clear();
    }
}