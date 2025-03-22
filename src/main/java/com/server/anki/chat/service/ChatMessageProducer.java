package com.server.anki.chat.service;

import com.server.anki.chat.dto.ChatMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 聊天消息生产者服务
 * 负责将聊天消息发送到消息队列
 */
@Service
public class ChatMessageProducer {
    private static final Logger logger = LoggerFactory.getLogger(ChatMessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 交换机和路由配置
    private static final String CHAT_EXCHANGE = "chat.exchange";
    private static final String CHAT_ROUTING_KEY = "chat.routing.key";

    /**
     * 发送聊天消息到消息队列
     */
    public void sendChatMessage(ChatMessageDTO message) {
        try {
            logger.info("发送聊天消息到队列，用户ID: {}, 工单ID: {}",
                    message.getUserId(), message.getTicketId());

            rabbitTemplate.convertAndSend(
                    CHAT_EXCHANGE,
                    CHAT_ROUTING_KEY,
                    message
            );

            logger.info("消息发送成功");
        } catch (Exception e) {
            logger.error("发送消息到队列时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送错误消息到消息队列
     */
    public void sendErrorMessage(String errorMessage, String sessionId) {
        try {
            ChatMessageDTO message = ChatMessageDTO.createErrorMessage(
                    errorMessage, sessionId);

            rabbitTemplate.convertAndSend(
                    CHAT_EXCHANGE,
                    CHAT_ROUTING_KEY,
                    message
            );

            logger.info("错误消息发送成功");
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
}