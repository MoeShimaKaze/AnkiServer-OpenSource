package com.server.anki.message.service;

import com.server.anki.message.Message;
import com.server.anki.message.MessageType;
import com.server.anki.config.RabbitMQConfig;
import com.server.anki.message.NotificationDTO;
import com.server.anki.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MessageProducerService {
    private static final Logger logger = LoggerFactory.getLogger(MessageProducerService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(User user, String content, MessageType type, Long ticketId) {
        logger.info("准备发送消息到队列，用户ID: {}, 类型: {}", user.getId(), type);
        try {
            // 创建消息对象
            Message message = new Message();

            // 创建简化的用户对象，只包含必要信息
            User simpleUser = new User();
            simpleUser.setId(user.getId());
            simpleUser.setUsername(user.getUsername());
            message.setUser(simpleUser);
            message.setUserId(user.getId()); // 设置冗余的userId字段

            message.setContent(content);
            message.setType(type);
            message.setCreatedDate(LocalDateTime.now());

            // 发送消息
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.MESSAGE_EXCHANGE,
                    RabbitMQConfig.MESSAGE_ROUTING_KEY,
                    message
            );

            // 处理通知
            if (shouldSendNotification(type)) {
                NotificationDTO notification = new NotificationDTO(
                        user.getId(), content, type.toString(), ticketId
                );

                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.NOTIFICATION_EXCHANGE,
                        RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                        notification
                );
            }

            logger.info("消息成功发送到队列，用户ID: {}", user.getId());
        } catch (Exception e) {
            logger.error("发送消息到队列时发生错误，用户ID: {}, 错误: {}",
                    user.getId(), e.getMessage(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    private boolean shouldSendNotification(MessageType type) {
        return type == MessageType.TICKET_STATUS_UPDATED ||
                type == MessageType.ORDER_STATUS_UPDATED ||
                type == MessageType.REVIEW_RECEIVED;
    }
}