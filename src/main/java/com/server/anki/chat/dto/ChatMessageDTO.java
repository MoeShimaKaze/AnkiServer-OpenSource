package com.server.anki.chat.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 聊天消息队列传输对象
 * 用于在消息队列中传递聊天消息
 */
@Getter
@Setter
public class ChatMessageDTO {
    private Long userId;
    private Long ticketId;
    private String message;
    private LocalDateTime timestamp;
    private String sessionId;  // WebSocket会话ID
    private MessageAction action;  // 消息动作类型

    // 消息动作枚举
    public enum MessageAction {
        SEND,       // 发送消息
        BROADCAST,  // 广播消息
        ERROR      // 错误消息
    }

    // 构造函数
    public ChatMessageDTO() {
        this.timestamp = LocalDateTime.now();
    }

    // 创建发送消息的静态工厂方法
    public static ChatMessageDTO createSendMessage(Long userId, Long ticketId, String message, String sessionId) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setUserId(userId);
        dto.setTicketId(ticketId);
        dto.setMessage(message);
        dto.setSessionId(sessionId);
        dto.setAction(MessageAction.SEND);
        return dto;
    }

    // 创建广播消息的静态工厂方法
    public static ChatMessageDTO createBroadcastMessage(Long ticketId, String message) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setTicketId(ticketId);
        dto.setMessage(message);
        dto.setAction(MessageAction.BROADCAST);
        return dto;
    }

    // 创建错误消息的静态工厂方法
    public static ChatMessageDTO createErrorMessage(String message, String sessionId) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setMessage(message);
        dto.setSessionId(sessionId);
        dto.setAction(MessageAction.ERROR);
        return dto;
    }
}