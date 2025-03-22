package com.server.anki.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.auth.token.TokenService;
import com.server.anki.message.NotificationDTO;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    // 存储已认证用户的WebSocket会话
    private final ConcurrentHashMap<Long, WebSocketSession> authenticatedSessions = new ConcurrentHashMap<>();
    // 存储用户最后活动时间
    private final ConcurrentHashMap<Long, Long> lastActivityTime = new ConcurrentHashMap<>();
    // 存储有效会话ID集合
    private final Set<String> validSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 会话超时时间设置为30秒
    private static final long SESSION_TIMEOUT = 30000;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("通知WebSocket连接尝试: {}", session.getId());
        try {
            // 从握手请求中获取并验证访问令牌
            String accessToken = extractAccessTokenFromHandshake(session);
            if (accessToken == null || !tokenService.validateAccessToken(accessToken)) {
                logger.warn("WebSocket连接中的访问令牌无效");
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("访问令牌无效"));
                return;
            }

            // 获取并验证用户ID
            Long userId = tokenService.getUserIdFromToken(accessToken);
            if (userId == null) {
                logger.warn("无法从令牌中提取用户ID");
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("用户ID无效"));
                return;
            }

            // 检查用户是否已有活动会话
            WebSocketSession existingSession = authenticatedSessions.get(userId);
            if (existingSession != null && existingSession.isOpen()) {
                logger.warn("正在关闭用户已存在的通知会话: {}", userId);
                existingSession.close(CloseStatus.POLICY_VIOLATION.withReason("新连接已建立"));
                authenticatedSessions.remove(userId);
            }

            // 保存新的会话信息
            session.getAttributes().put("userId", userId);
            authenticatedSessions.put(userId, session);
            lastActivityTime.put(userId, System.currentTimeMillis());
            validSessionIds.add(session.getId());

            // 发送连接成功消息
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "CONNECTION_ESTABLISHED",
                            "userId", userId,
                            "message", "通知连接已成功建立",
                            "timestamp", System.currentTimeMillis()
                    )
            )));
            logger.info("用户通知WebSocket连接已建立: {}", userId);

        } catch (Exception e) {
            logger.error("建立通知连接时发生错误", e);
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("服务器内部错误"));
            } catch (IOException ex) {
                logger.error("关闭WebSocket会话时发生错误", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            authenticatedSessions.remove(userId);
            lastActivityTime.remove(userId);
            validSessionIds.remove(session.getId());
            logger.info("用户 {} 的通知WebSocket连接已关闭。状态: {}", userId, status);
        } else {
            logger.warn("未知用户的通知WebSocket连接已关闭。会话ID: {}, 状态: {}", session.getId(), status);
        }
    }

    public void sendNotification(Long userId, String content, String type, Long ticketId) {
        WebSocketSession session = authenticatedSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                NotificationDTO notification = new NotificationDTO(userId, content, type, ticketId);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
                // 更新最后活动时间
                lastActivityTime.put(userId, System.currentTimeMillis());
                logger.info("已向用户 {} 发送通知。类型: {}, 工单ID: {}", userId, type, ticketId);
            } catch (IOException e) {
                logger.error("向用户 {} 发送通知时发生错误", userId, e);
                handleFailedNotification(session, userId);
            }
        } else {
            logger.warn("无法发送通知。用户 {} 未连接或会话已关闭", userId);
        }
    }

    public void sendNotificationToAdmins(String content, Long ticketId) {
        List<User> admins = userService.getAllAdmins();
        logger.info("正在向 {} 个管理员发送工单 {} 的通知", admins.size(), ticketId);

        for (User admin : admins) {
            if (isUserAuthenticated(admin.getId())) {
                sendNotification(admin.getId(), content, "NEW_MESSAGE", ticketId);
            }
        }
    }

    private String extractAccessTokenFromHandshake(WebSocketSession session) {
        List<String> cookies = session.getHandshakeHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                for (String cookiePair : cookie.split(";")) {
                    String trimmedCookie = cookiePair.trim();
                    if (trimmedCookie.startsWith("access_token=")) {
                        return trimmedCookie.substring("access_token=".length());
                    }
                }
            }
        }
        return null;
    }

    private void handleFailedNotification(WebSocketSession session, Long userId) {
        try {
            session.close(CloseStatus.SERVER_ERROR.withReason("通知发送失败"));
            authenticatedSessions.remove(userId);
            lastActivityTime.remove(userId);
            validSessionIds.remove(session.getId());
        } catch (IOException e) {
            logger.error("关闭失败会话时发生错误，用户ID: {}", userId, e);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "ERROR",
                            "message", errorMessage,
                            "timestamp", System.currentTimeMillis()
                    )
            )));
        } catch (IOException e) {
            logger.error("发送错误消息时发生错误", e);
        }
    }

    public boolean isUserAuthenticated(Long userId) {
        WebSocketSession session = authenticatedSessions.get(userId);
        return session != null && session.isOpen();
    }

    // 定期检查会话超时
    @Scheduled(fixedRate = 15000) // 每15秒检查一次
    public void checkSessionTimeouts() {
        long now = System.currentTimeMillis();
        authenticatedSessions.forEach((userId, session) -> {
            Long lastActivity = lastActivityTime.get(userId);
            if (lastActivity != null && now - lastActivity > SESSION_TIMEOUT) {
                try {
                    logger.warn("用户 {} 的会话已超时", userId);
                    session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("会话超时"));
                    authenticatedSessions.remove(userId);
                    lastActivityTime.remove(userId);
                    validSessionIds.remove(session.getId());
                } catch (IOException e) {
                    logger.error("关闭超时会话时发生错误，用户ID: {}", userId, e);
                }
            }
        });
    }
}