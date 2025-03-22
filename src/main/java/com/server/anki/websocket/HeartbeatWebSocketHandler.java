package com.server.anki.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.auth.token.TokenService;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
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
import java.util.stream.Collectors;

@Component
public class HeartbeatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatWebSocketHandler.class);

    private final ConcurrentHashMap<Long, WebSocketSession> authenticatedSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final Set<String> validSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final long HEARTBEAT_TIMEOUT = 30000; // 30秒超时

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket heartbeat connection established: {}", session.getId());
        try {
            // 从handshake中获取token
            String accessToken = extractAccessTokenFromHandshake(session);
            if (accessToken == null || !tokenService.validateAccessToken(accessToken)) {
                logger.warn("Invalid access token in WebSocket connection");
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
                return;
            }

            // 验证通过，保存认证信息
            Long userId = tokenService.getUserIdFromToken(accessToken);
            session.getAttributes().put("userId", userId);
            authenticatedSessions.put(userId, session);
            lastHeartbeatTime.put(userId, System.currentTimeMillis());
            validSessionIds.add(session.getId());

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "CONNECTION_ESTABLISHED",
                            "userId", userId,
                            "message", "WebSocket connection authenticated"
                    )
            )));
            logger.info("Heartbeat WebSocket authenticated for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error in heartbeat connection establishment", e);
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("Internal Server Error"));
            } catch (IOException ex) {
                logger.error("Error closing WebSocket session", ex);
            }
        }
    }

    @Override
    public void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String messageType = jsonNode.get("type").asText();
            logger.debug("Received message of type: {}", messageType);

            if ("PING".equals(messageType)) {
                handlePing(session);
            } else {
                logger.warn("Unhandled message type: {}", messageType);
                sendErrorMessage(session, "Unsupported message type: " + messageType);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
            sendErrorMessage(session, "Error processing message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            authenticatedSessions.remove(userId);
            lastHeartbeatTime.remove(userId);
            validSessionIds.remove(session.getId());
            logger.info("WebSocket connection closed for User ID: {}. Status: {}", userId, status);
        } else {
            logger.warn("WebSocket connection closed for unknown user. Session ID: {}, Status: {}", session.getId(), status);
        }
    }

    private void handlePing(WebSocketSession session) throws IOException {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            logger.warn("Ping received from unauthenticated session");
            sendErrorMessage(session, "Unauthenticated connection");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }

        lastHeartbeatTime.put(userId, System.currentTimeMillis());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of(
                        "type", "PONG",
                        "timestamp", System.currentTimeMillis(),
                        "userId", userId
                )
        )));
        logger.debug("Responded to PING with PONG for user: {}", userId);
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

    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of(
                        "type", "ERROR",
                        "message", errorMessage
                )
        )));
    }

    // 提供给其他WebSocket handler使用的验证方法
    public boolean isUserAuthenticated(Long userId) {
        WebSocketSession session = authenticatedSessions.get(userId);
        return session != null && session.isOpen();
    }

    // 获取当前活跃用户数
    public int getActiveSessionCount() {
        return (int) authenticatedSessions.entrySet().stream()
                .filter(entry -> entry.getValue().isOpen())
                .count();
    }
    /**
     * 获取在线管理员列表
     * @return 在线管理员列表
     */
    public List<User> getActiveAdmins() {
        return authenticatedSessions.entrySet().stream()
                .filter(entry -> entry.getValue().isOpen())
                .map(entry -> userRepository.findById(entry.getKey()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(user -> userService.isAdminUser(user))
                .collect(Collectors.toList());
    }

    // 在HeartbeatWebSocketHandler中添加
    public Long getUserIdFromSession(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        Long userId = (Long) session.getAttributes().get("userId");
        // 确保这个userId对应的session是已认证的
        if (userId != null && isUserAuthenticated(userId)) {
            return userId;
        }
        return null;
    }

    // 定期检查心跳超时
    @Scheduled(fixedRate = 15000) // 每15秒检查一次
    public void checkHeartbeats() {
        long now = System.currentTimeMillis();
        authenticatedSessions.forEach((userId, session) -> {
            Long lastHeartbeat = lastHeartbeatTime.get(userId);
            if (lastHeartbeat != null && now - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                try {
                    logger.warn("Heartbeat timeout for user: {}", userId);
                    session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("Heartbeat timeout"));
                    authenticatedSessions.remove(userId);
                    lastHeartbeatTime.remove(userId);
                    validSessionIds.remove(session.getId());
                } catch (IOException e) {
                    logger.error("Error closing timed out session for user: {}", userId, e);
                }
            }
        });
    }
}