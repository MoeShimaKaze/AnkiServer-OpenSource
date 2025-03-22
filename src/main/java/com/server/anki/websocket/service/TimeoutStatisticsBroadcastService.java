package com.server.anki.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.timeout.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TimeoutStatisticsBroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutStatisticsBroadcastService.class);

    @Autowired
    private ObjectMapper objectMapper;

    // 存储会话信息
    private final Map<Long, Set<WebSocketSession>> userSessionsMap = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> adminSessions = ConcurrentHashMap.newKeySet();

    /**
     * 注册用户会话
     */
    public void registerUserSession(Long userId, WebSocketSession session) {
        userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        logger.debug("已注册用户 {} 的会话: {}", userId, session.getId());
    }

    /**
     * 注册管理员会话
     */
    public void registerAdminSession(WebSocketSession session) {
        adminSessions.add(session);
        logger.debug("已注册管理员会话: {}", session.getId());
    }

    /**
     * 获取管理员会话集合
     * @return 管理员会话集合
     */
    public Set<WebSocketSession> getAdminSessions() {
        return Collections.unmodifiableSet(adminSessions);
    }

    /**
     * 获取指定用户的会话集合
     * @param userId 用户ID
     * @return 用户会话集合，如果没有则返回null
     */
    public Set<WebSocketSession> getUserSessions(Long userId) {
        return userSessionsMap.get(userId);
    }

    /**
     * 获取所有用户会话映射
     * @return 用户会话映射（只读）
     */
    public Map<Long, Set<WebSocketSession>> getUserSessionsMap() {
        return Collections.unmodifiableMap(userSessionsMap);
    }
    /**
     * 移除会话
     */
    public void removeSession(WebSocketSession session) {
        // 从用户会话中移除
        for (Set<WebSocketSession> sessions : userSessionsMap.values()) {
            sessions.remove(session);
        }

        // 清理空的用户会话集合
        userSessionsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 从管理员会话中移除
        adminSessions.remove(session);

        logger.debug("已移除会话: {}", session.getId());
    }

    /**
     * 广播用户超时统计更新
     */
    public void broadcastUserStatisticsUpdate(Long userId, UserTimeoutStatistics statistics) {
        Set<WebSocketSession> sessions = userSessionsMap.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "user",
                        "data", statistics,
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向用户 {} 发送超时统计更新失败", userId, e);
                        }
                    }
                }

                logger.debug("已向用户 {} 的 {} 个会话广播超时统计更新", userId, sessions.size());
            } catch (Exception e) {
                logger.error("序列化超时统计更新消息失败", e);
            }
        }
    }

    /**
     * 广播系统超时统计更新
     */
    public void broadcastSystemStatisticsUpdate(SystemTimeoutStatistics statistics) {
        if (!adminSessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "system",
                        "data", statistics,
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : adminSessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向管理员发送系统超时统计更新失败", e);
                        }
                    }
                }

                logger.debug("已向 {} 个管理员会话广播系统超时统计更新", adminSessions.size());
            } catch (Exception e) {
                logger.error("序列化系统超时统计更新消息失败", e);
            }
        }
    }

    /**
     * 广播全局超时统计更新
     */
    public void broadcastGlobalStatisticsUpdate(SystemTimeoutStatistics statistics) {
        if (!adminSessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "global",
                        "data", statistics,
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : adminSessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向管理员发送全局超时统计更新失败", e);
                        }
                    }
                }

                logger.debug("已向 {} 个管理员会话广播全局超时统计更新", adminSessions.size());
            } catch (Exception e) {
                logger.error("序列化全局超时统计更新消息失败", e);
            }
        }
    }

    /**
     * 新增：广播超时报告更新
     */
    public void broadcastTimeoutReportUpdate(GlobalTimeoutReport report) {
        if (!adminSessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "timeoutReport",
                        "data", report,
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : adminSessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向管理员发送超时报告更新失败", e);
                        }
                    }
                }

                logger.debug("已向 {} 个管理员会话广播超时报告更新", adminSessions.size());
            } catch (Exception e) {
                logger.error("序列化超时报告更新消息失败", e);
            }
        }
    }

    /**
     * 新增：广播超时建议更新给管理员
     */
    public void broadcastRecommendationsUpdate(List<String> recommendations, StatisticsPeriod period) {
        if (!adminSessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "recommendations",
                        "recommendations", recommendations,
                        "period", Map.of(
                                "startTime", period.startTime(),
                                "endTime", period.endTime()
                        ),
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : adminSessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向管理员发送超时建议更新失败", e);
                        }
                    }
                }

                logger.debug("已向 {} 个管理员会话广播超时建议更新", adminSessions.size());
            } catch (Exception e) {
                logger.error("序列化超时建议更新消息失败", e);
            }
        }
    }

    /**
     * 新增：广播用户超时建议更新
     */
    public void broadcastUserRecommendationsUpdate(Long userId, List<String> recommendations, StatisticsPeriod period) {
        Set<WebSocketSession> sessions = userSessionsMap.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            try {
                Map<String, Object> message = Map.of(
                        "type", "recommendations",
                        "recommendations", recommendations,
                        "period", Map.of(
                                "startTime", period.startTime(),
                                "endTime", period.endTime()
                        ),
                        "timestamp", LocalDateTime.now().toString()
                );

                String json = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(json);

                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向用户 {} 发送超时建议更新失败", userId, e);
                        }
                    }
                }

                logger.debug("已向用户 {} 的 {} 个会话广播超时建议更新", userId, sessions.size());
            } catch (Exception e) {
                logger.error("序列化用户超时建议更新消息失败", e);
            }
        }
    }

    /**
     * 获取活跃用户会话数
     */
    public int getActiveUserSessionCount() {
        return userSessionsMap.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * 获取活跃管理员会话数
     */
    public int getActiveAdminSessionCount() {
        return adminSessions.size();
    }
}