package com.server.anki.message.service;

import com.server.anki.user.User;
import com.server.anki.websocket.NotificationWebSocketHandler;
import com.server.anki.message.Message;
import com.server.anki.message.MessageRepository;
import com.server.anki.config.RabbitMQConfig;
import com.server.anki.message.NotificationDTO;
import com.server.anki.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MessageConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerService.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @RabbitListener(queues = RabbitMQConfig.MESSAGE_QUEUE)
    @Transactional
    public void processMessage(Message message) {
        try {
            // 首先验证消息的完整性
            validateMessage(message);

            // 重新加载完整的用户信息
            User user = userRepository.findById(message.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "未找到用户: " + message.getUserId()));
            message.setUser(user);

            logger.info("处理消息，用户ID: {}, 类型: {}", user.getId(), message.getType());

            Message savedMessage = messageRepository.save(message);
            logger.info("消息已保存到数据库，消息ID: {}", savedMessage.getId());

        } catch (Exception e) {
            logger.error("处理消息时发生错误: {}", e.getMessage(), e);
            // 抛出特定异常以触发死信队列处理
            throw new AmqpRejectAndDontRequeueException("消息处理失败", e);
        }
    }

    private void validateMessage(Message message) {
        if (message.getUserId() == null) {
            throw new IllegalArgumentException("消息缺少用户ID");
        }
        if (message.getType() == null) {
            throw new IllegalArgumentException("消息缺少��型信息");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void processNotification(NotificationDTO notification) {
        try {
            logger.info("接收到新通知，用户ID: {}, 类型: {}",
                    notification.getUserId(), notification.getType());

            notificationWebSocketHandler.sendNotification(
                    notification.getUserId(),
                    notification.getContent(),
                    notification.getType(),
                    notification.getTicketId()
            );

            logger.info("通知已发送到用户，用户ID: {}", notification.getUserId());
        } catch (Exception e) {
            logger.error("发送通知时发生错误，用户ID: {}, 错误: {}",
                    notification.getUserId(), e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("通知发送失败", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.DEAD_LETTER_QUEUE)
    public void processFailedMessages(Message message) {
        Long userId = Optional.ofNullable(message.getUser())
                .map(User::getId)
                .orElse(message.getUserId());

        logger.error("消息处理失败，进入死信队列。用户ID: {}, 类型: {}, 失败原因: {}",
                userId, message.getType(), message.getFailureReason());
    }
}