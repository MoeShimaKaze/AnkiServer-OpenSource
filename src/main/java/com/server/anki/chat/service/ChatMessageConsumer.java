package com.server.anki.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.server.anki.chat.entity.Chat;
import com.server.anki.chat.dto.ChatMessageDTO;
import com.server.anki.chat.ChatRepository;
import com.server.anki.ticket.Ticket;
import com.server.anki.ticket.TicketRepository;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.websocket.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import org.springframework.web.socket.WebSocketSession;


/**
 * 聊天消息消费者服务
 * 负责处理从消息队列接收的聊天消息
 */


@Service
public class ChatMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageConsumer.class);

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ChatWebSocketHandler webSocketHandler;

    public ChatMessageConsumer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
    /**
     * 处理从消息队列接收的聊天消息
     */
    @RabbitListener(queues = "chat.queue")
    @Transactional
    public void processChatMessage(ChatMessageDTO message) {
        try {
            logger.info("接收到聊天消息，用户ID: {}, 工单ID: {}",
                    message.getUserId(), message.getTicketId());

            switch (message.getAction()) {
                case SEND -> handleSendMessage(message);
                case BROADCAST -> handleBroadcastMessage(message);
                case ERROR -> handleErrorMessage(message);
                default -> logger.warn("未知的消息动作类型: {}", message.getAction());
            }
        } catch (Exception e) {
            logger.error("处理聊天消息时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理发送消息
     */
    private void handleSendMessage(ChatMessageDTO message) throws Exception {
        // 获取用户和工单信息
        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new IllegalStateException("用户不存在"));

        Ticket ticket = ticketRepository.findById(message.getTicketId())
                .orElseThrow(() -> new IllegalStateException("工单不存在"));

        // 创建并保存聊天记录
        Chat chat = new Chat();
        chat.setMessage(message.getMessage());
        chat.setTimestamp(message.getTimestamp());
        chat.setUser(user);
        chat.setTicket(ticket);

        Chat savedChat = chatRepository.save(chat);

        // 广播消息给所有相关会话
        broadcastToTicketSessions(ticket.getId(), savedChat);

        logger.info("聊天消息处理完成");
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(ChatMessageDTO message) throws Exception {
        broadcastToTicketSessions(message.getTicketId(), message.getMessage());
        logger.info("广播消息发送完成");
    }

    /**
     * 处理错误消息
     */
    private void handleErrorMessage(ChatMessageDTO message) throws Exception {
        WebSocketSession session = webSocketHandler.getSession(message.getSessionId());
        if (session != null && session.isOpen()) {
            webSocketHandler.sendErrorMessage(session, message.getMessage());
            logger.info("错误消息发送完成");
        }
    }

    /**
     * 向工单相关的所有WebSocket会话广播消息
     */
    private void broadcastToTicketSessions(Long ticketId, Object message) throws Exception {
        Set<WebSocketSession> sessions = webSocketHandler.getTicketSessions(ticketId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    webSocketHandler.sendMessage(session, message);
                }
            }
        }
    }
}