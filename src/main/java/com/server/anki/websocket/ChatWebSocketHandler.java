package com.server.anki.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.server.anki.chat.dto.ChatDTO;
import com.server.anki.chat.dto.ChatMessageDTO;
import com.server.anki.chat.entity.Chat;
import com.server.anki.chat.service.ChatMessageProducer;
import com.server.anki.ticket.Ticket;
import com.server.anki.ticket.TicketDTO;
import com.server.anki.ticket.TicketRepository;
import com.server.anki.user.User;
import com.server.anki.auth.AuthenticationService;
import com.server.anki.user.UserDTO;
import com.server.anki.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * WebSocket处理器
 * 负责处理WebSocket连接和消息的异步转发
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    // 注入所需服务

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ChatMessageProducer messageProducer;

    @Autowired
    private UserRepository userRepository;  // 注入UserRepository

    // 注入ChatRepository

    // 修改 ObjectMapper 的初始化
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        // 注册JavaTimeModule以支持Java 8日期时间类型
        objectMapper.registerModule(new JavaTimeModule());
        // 配置日期时间格式
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    // 会话管理（保持原有的会话管理结构）
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> ticketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionTickets = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        logger.info("正在建立WebSocket连接: {}", session.getId());

        try {
            // 验证用户身份
            String token = extractTokenFromHandshake(session);
            if (token == null) {
                logger.warn("未找到身份验证令牌");
                sendErrorAndClose(session, "未找到身份验证令牌");
                return;
            }

            User user = authenticationService.getAuthenticatedUserFromToken(token);
            if (user == null) {
                logger.warn("用户身份验证失败");
                sendErrorAndClose(session, "用户身份验证失败");
                return;
            }

            // 解析工单ID
            Long ticketId = extractTicketId(session);
            if (ticketId == null) {
                logger.warn("无效的工单ID");
                sendErrorAndClose(session, "无效的工单ID");
                return;
            }

            // 验证工单访问权限
            if (!validateTicketAccess(user, ticketId)) {
                logger.warn("用户无权访问此工单. 用户ID: {}, 工单ID: {}", user.getId(), ticketId);
                sendErrorAndClose(session, "您没有权限访问此工单");
                return;
            }

            // 保存会话信息
            sessions.put(session.getId(), session);
            ticketSessions.computeIfAbsent(ticketId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionTickets.put(session.getId(), ticketId);
            session.getAttributes().put("userId", user.getId());

            // 发送连接成功消息
            sendConnectionSuccess(session);
            logger.info("WebSocket连接建立成功. 会话ID: {}, 用户ID: {}, 工单ID: {}",
                    session.getId(), user.getId(), ticketId);
        } catch (Exception e) {
            logger.error("建立连接时发生错误: {}", e.getMessage(), e);
            sendErrorAndClose(session, "建立连接失败：" + e.getMessage());
        }
    }

    // 修改消息处理方法
    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            Long userId = (Long) session.getAttributes().get("userId");
            Long ticketId = sessionTickets.get(session.getId());

            if (userId == null || ticketId == null) {
                logger.warn("会话信息不完整");
                sendErrorMessage(session, "会话信息不完整，请重新连接");
                return;
            }

            // 创建并发送消息
            ChatMessageDTO chatMessage = createChatMessageDTO(userId, ticketId,
                    jsonNode.get("message").asText(), session.getId());
            messageProducer.sendChatMessage(chatMessage);

        } catch (Exception e) {
            logger.error("处理消息时发生错误", e);
            try {
                sendErrorMessage(session, "消息处理失败：" + e.getMessage());
            } catch (IOException ex) {
                logger.error("发送错误消息失败", ex);
            }
        }
    }

    // 新增：创建消息DTO的辅助方法
    private ChatMessageDTO createChatMessageDTO(Long userId, Long ticketId,
                                                String message, String sessionId) {

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setUserId(userId);
        dto.setTicketId(ticketId);
        dto.setMessage(message);
        dto.setSessionId(sessionId);
        dto.setTimestamp(LocalDateTime.now());
        dto.setAction(ChatMessageDTO.MessageAction.SEND);
        return dto;
    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        logger.info("WebSocket连接关闭: {}, 状态: {}", session.getId(), status);

        try {
            // 清理会话数据
            Long ticketId = sessionTickets.remove(session.getId());
            sessions.remove(session.getId());

            if (ticketId != null) {
                CopyOnWriteArraySet<WebSocketSession> ticketGroup = ticketSessions.get(ticketId);
                if (ticketGroup != null) {
                    ticketGroup.remove(session);
                    if (ticketGroup.isEmpty()) {
                        ticketSessions.remove(ticketId);
                    }
                }
            }

            logger.info("会话清理完成");
        } catch (Exception e) {
            logger.error("清理会话时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 向指定会话发送错误消息
     */
    public void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, Object> errorResponse = Map.of(
                "type", "ERROR",
                "message", errorMessage
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }

    /**
     * 向指定会话发送消息
     */
    public void sendMessage(WebSocketSession session, Object message) throws IOException {
        try {
            Object enrichedMessage = message;

            // 如果是Chat对象，确保包含完整信息
            if (message instanceof Chat chat) {
                enrichedMessage = enrichChatMessage(chat);
            }
            // 如果是数组或集合，处理每个元素
            else if (message instanceof Collection<?> messages) {
                enrichedMessage = messages.stream()
                        .map(msg -> msg instanceof Chat ? enrichChatMessage((Chat) msg) : msg)
                        .collect(Collectors.toList());
            }

            // 发送消息
            String jsonMessage = objectMapper.writeValueAsString(enrichedMessage);
            session.sendMessage(new TextMessage(jsonMessage));

        } catch (Exception e) {
            logger.error("发送消息时发生错误", e);
            sendErrorMessage(session, "消息发送失败：" + e.getMessage());
        }
    }

    // 新增：消息充实方法
    private ChatDTO enrichChatMessage(Chat chat) {
        try {
            // 创建充实的DTO对象
            ChatDTO chatDTO = new ChatDTO();
            chatDTO.setId(chat.getId());
            chatDTO.setMessage(chat.getMessage());
            chatDTO.setTimestamp(chat.getTimestamp());

            // 充实用户信息
            if (chat.getUser() != null) {
                User user = userRepository.findById(chat.getUser().getId())
                        .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

                UserDTO userDTO = new UserDTO();
                userDTO.setId(user.getId());
                userDTO.setUsername(user.getUsername());
                userDTO.setEmail(user.getEmail());
                userDTO.setUserGroup(user.getUserGroup());
                userDTO.setUserVerificationStatus(user.getUserVerificationStatus());
                chatDTO.setUser(userDTO);
            }

            // 充实工单信息
            if (chat.getTicket() != null) {
                Ticket ticket = ticketRepository.findById(chat.getTicket().getId())
                        .orElseThrow(() -> new EntityNotFoundException("工单不存在"));

                TicketDTO ticketDTO = getTicketDTO(ticket);
                chatDTO.setTicket(ticketDTO);
            }

            return chatDTO;
        } catch (Exception e) {
            logger.error("充实消息时发生错误", e);
            throw new RuntimeException("消息处理失败", e);
        }
    }

    @NotNull
    private static TicketDTO getTicketDTO(Ticket ticket) {
        TicketDTO ticketDTO = new TicketDTO();
        ticketDTO.setId(ticket.getId());
        ticketDTO.setIssue(ticket.getIssue());
        ticketDTO.setType(ticket.getType());
        ticketDTO.setCreatedDate(ticket.getCreatedDate());
        ticketDTO.setClosedDate(ticket.getClosedDate());
        ticketDTO.setOpen(ticket.isOpen());
        ticketDTO.setUserId(ticket.getUser().getId());
        ticketDTO.setClosedByAdmin(ticket.isClosedByAdmin());
        if (ticket.getAssignedAdmin() != null) {
            ticketDTO.setAssignedAdminId(ticket.getAssignedAdmin().getId());
        }
        return ticketDTO;
    }

    /**
     * 获取指定工单的所有会话
     */
    public Set<WebSocketSession> getTicketSessions(Long ticketId) {
        return ticketSessions.get(ticketId);
    }

    /**
     * 根据会话ID获取会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 发送连接成功消息
     */
    private void sendConnectionSuccess(WebSocketSession session) throws IOException {
        Map<String, String> response = new HashMap<>();
        response.put("type", "CONNECTION_ESTABLISHED");
        response.put("message", "连接成功");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送错误消息并关闭连接
     */
    private void sendErrorAndClose(WebSocketSession session, String errorMessage) throws IOException {
        sendErrorMessage(session, errorMessage);
        session.close(CloseStatus.NOT_ACCEPTABLE);
    }

    /**
     * 从会话中提取工单ID
     */
    private Long extractTicketId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path != null) {
            Map<String, String> pathVariables = new UriTemplate("/ws/chat/{ticketId}").match(path);
            String ticketIdStr = pathVariables.get("ticketId");
            if (ticketIdStr != null && ticketIdStr.matches("\\d+")) {
                return Long.valueOf(ticketIdStr);
            }
        }
        return null;
    }

    /**
     * 从WebSocket握手请求中提取token
     */
    private String extractTokenFromHandshake(WebSocketSession session) {
        String cookieHeader = session.getHandshakeHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split("; ");
            for (String cookie : cookies) {
                if (cookie.startsWith("access_token=")) {
                    return cookie.substring("access_token=".length());
                }
            }
        }
        return null;
    }

    /**
     * 验证用户是否有权限访问工单
     */
    private boolean validateTicketAccess(User user, Long ticketId) {
        try {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

            return ticket.getUser().getId().equals(user.getId()) ||
                    "admin".equals(user.getUserGroup());
        } catch (Exception e) {
            logger.error("验证工单访问权限时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }
}