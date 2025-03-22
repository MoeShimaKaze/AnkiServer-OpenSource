package com.server.anki.config;

import com.server.anki.websocket.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private HeartbeatWebSocketHandler heartbeatWebSocketHandler;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private TimeoutStatisticsWebSocketHandler timeoutStatisticsWebSocketHandler;

    @Value("${ssl.mode:none}")
    private String sslMode;

    @Value("${app.domain:}")
    private String appDomain;

    @Override
    public void registerWebSocketHandlers(@NotNull WebSocketHandlerRegistry registry) {
        // 创建允许的来源列表
        List<String> allowedOrigins = new ArrayList<>();
        allowedOrigins.add("http://localhost:3000");

        // 根据SSL模式添加额外的来源
        if ("direct".equals(sslMode) || "proxy".equals(sslMode)) {
            allowedOrigins.add("https://localhost:3000");

            // 如果配置了应用域名，也添加
            if (appDomain != null && !appDomain.isEmpty()) {
                allowedOrigins.add("https://" + appDomain);
            }
        }

        // 转换为数组
        String[] originsArray = allowedOrigins.toArray(new String[0]);

        registry.addHandler(heartbeatWebSocketHandler, "/ws/heartbeat")
                .setAllowedOrigins(originsArray);

        registry.addHandler(chatWebSocketHandler, "/ws/chat/{ticketId}")
                .setAllowedOrigins(originsArray);

        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .setAllowedOrigins(originsArray);

        // 添加超时统计WebSocket处理器
        registry.addHandler(timeoutStatisticsWebSocketHandler, "/ws/timeout-statistics")
                .setAllowedOrigins(originsArray);
    }
}