package com.server.anki.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.auth.token.TokenService;
import com.server.anki.timeout.model.*;
import com.server.anki.timeout.service.GlobalTimeoutStatisticsService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.websocket.service.TimeoutStatisticsBroadcastService;
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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 超时统计WebSocket处理程序
 * 负责处理前端与后端超时统计数据的实时交互
 */
@Component
public class TimeoutStatisticsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutStatisticsWebSocketHandler.class);

    // 使用合并后的GlobalTimeoutStatisticsService替代原有的TimeoutStatisticsService
    @Autowired
    private GlobalTimeoutStatisticsService globalTimeoutStatisticsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private TimeoutStatisticsBroadcastService broadcastService;

    // 存储会话相关信息
    private final Set<String> validSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * WebSocket连接建立处理
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        logger.info("超时统计WebSocket连接尝试: {}", session.getId());
        try {
            // 从握手请求中获取并验证访问令牌
            String accessToken = extractAccessTokenFromHandshake(session);
            if (accessToken == null) {
                logger.warn("未找到访问令牌，尝试从URL参数获取");
                accessToken = extractTokenFromUrl(session.getUri());
            }

            if (accessToken == null || !tokenService.validateAccessToken(accessToken)) {
                logger.warn("WebSocket连接中的访问令牌无效或未找到");
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

            // 获取用户信息
            User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("找不到用户信息: {}", userId);
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("找不到用户信息"));
                return;
            }

            // 保存用户信息到会话属性
            session.getAttributes().put("userId", userId);
            session.getAttributes().put("userGroup", user.getUserGroup());
            validSessionIds.add(session.getId());

            // 获取请求类型参数
            Map<String, String> params = parseQueryParams(session.getUri());
            String type = params.getOrDefault("type", "user");

            // 检查权限
            if (("system".equals(type) || "global".equals(type)) && !"admin".equals(user.getUserGroup())) {
                logger.warn("非管理员用户 {} 尝试订阅系统超时统计", user.getId());
                session.close(CloseStatus.POLICY_VIOLATION.withReason("无权限访问系统统计"));
                return;
            }

            // 注册会话
            if ("admin".equals(user.getUserGroup())) {
                broadcastService.registerAdminSession(session);
                logger.info("管理员 {} 连接到超时统计WebSocket，类型: {}", user.getId(), type);
            } else {
                broadcastService.registerUserSession(user.getId(), session);
                logger.info("用户 {} 连接到超时统计WebSocket，类型: {}", user.getId(), type);
            }

            // 发送连接成功消息
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "CONNECTION_ESTABLISHED",
                            "userId", userId,
                            "message", "超时统计连接已成功建立",
                            "timestamp", LocalDateTime.now().toString()
                    )
            )));

            // 发送初始数据
            sendInitialData(session, user, type);

        } catch (Exception e) {
            logger.error("建立超时统计连接时发生错误", e);
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("服务器内部错误"));
            } catch (IOException ex) {
                logger.error("关闭WebSocket会话时发生错误", ex);
            }
        }
    }

    /**
     * WebSocket连接关闭处理
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            broadcastService.removeSession(session);
            validSessionIds.remove(session.getId());
            logger.info("用户 {} 的超时统计WebSocket连接已关闭。状态: {}", userId, status);
        } else {
            logger.warn("未知用户的超时统计WebSocket连接已关闭。会话ID: {}, 状态: {}", session.getId(), status);
        }
    }

    /**
     * WebSocket消息处理
     */
    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) throws Exception {
        // 验证会话有效性
        if (!validSessionIds.contains(session.getId())) {
            logger.warn("收到未认证会话的消息");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("未认证"));
            return;
        }

        try {
            // 解析客户端消息
            String payload = message.getPayload();
            Map<String, Object> request = objectMapper.readValue(payload, new TypeReference<>() {});

            // 获取用户信息
            Long userId = (Long) session.getAttributes().get("userId");
            String userGroup = (String) session.getAttributes().get("userGroup");

            if (userId == null || userGroup == null) {
                logger.warn("会话属性中缺少用户信息");
                session.close(CloseStatus.POLICY_VIOLATION.withReason("会话信息无效"));
                return;
            }

            User user = new User();
            user.setId(userId);
            user.setUserGroup(userGroup);

            String command = (String) request.get("command");

            switch (command) {
                case "getStatistics":
                    // 原有代码保持不变
                    String type = (String) request.get("type");

                    // 检查权限
                    if (("system".equals(type) || "global".equals(type)) && !"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求系统超时统计", user.getId());
                        return;
                    }

                    // 解析时间范围
                    LocalDateTime startTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime endTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod period = createPeriod(startTime, endTime);

                    // 获取并发送相应的统计数据
                    if ("user".equals(type)) {
                        UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatistics(user, period);
                        sendUserStatistics(session, statistics);
                    } else if ("system".equals(type) || "global".equals(type)) {
                        // "system"和"global"类型都使用相同的统计数据源
                        SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

                        if ("system".equals(type)) {
                            sendSystemStatistics(session, statistics);
                        } else {
                            sendGlobalStatistics(session, statistics);
                        }
                    }
                    break;

                case "getRanking":
                    // 原有代码保持不变
                    if (!"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求超时排行榜", user.getId());
                        return;
                    }

                    // 解析请求参数
                    LocalDateTime rankingStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime rankingEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod rankingPeriod = createPeriod(rankingStartTime, rankingEndTime);
                    String sortBy = (String) request.getOrDefault("sortBy", "timeoutRate");
                    String direction = (String) request.getOrDefault("direction", "desc");
                    Integer limit = (Integer) request.getOrDefault("limit", 10);

                    // 获取排行榜数据
                    List<?> rankings = globalTimeoutStatisticsService.getUserTimeoutRanking(
                            rankingPeriod, limit, sortBy, "asc".equalsIgnoreCase(direction));

                    // 发送排行榜数据
                    sendRankingData(session, rankings, sortBy, direction);
                    break;

                // 添加新的命令处理
                case "getRiskAnalysis":
                    if (!"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求风险分析", user.getId());
                        return;
                    }

                    // 解析请求参数
                    LocalDateTime riskStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime riskEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod riskPeriod = createPeriod(riskStartTime, riskEndTime);

                    // 获取系统统计数据
                    SystemTimeoutStatistics systemStats = globalTimeoutStatisticsService.getSystemStatistics(riskPeriod);

                    // 分析服务风险信息
                    Map<String, Object> riskAnalysis = new HashMap<>();
                    riskAnalysis.put("isHighRiskState", systemStats.isHighRiskState());
                    riskAnalysis.put("highRiskHour", systemStats.getHighRiskHour());
                    riskAnalysis.put("highRiskRegion", systemStats.getHighRiskRegion());
                    riskAnalysis.put("riskPatterns", systemStats.riskPatterns().stream()
                            .map(pattern -> Map.of(
                                    "pattern", pattern.pattern(),
                                    "occurrence", pattern.occurrence(),
                                    "riskLevel", pattern.riskLevel(),
                                    "description", pattern.description(),
                                    "isUrgent", pattern.isUrgent(),
                                    "riskLevelDescription", pattern.getRiskLevelDescription()
                            ))
                            .collect(Collectors.toList()));

                    // 发送风险分析数据
                    sendRiskAnalysis(session, riskAnalysis);
                    break;

                case "getUserRiskAssessment":
                    // 解析请求参数
                    LocalDateTime assessmentStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime assessmentEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod assessmentPeriod = createPeriod(assessmentStartTime, assessmentEndTime);

                    // 获取用户统计数据
                    UserTimeoutStatistics userStats = globalTimeoutStatisticsService.getUserStatistics(user, assessmentPeriod);

                    // 构建风险评估数据
                    Map<String, Object> riskAssessment = getRiskAssessment(userStats);

                    // 发送用户风险评估数据
                    sendUserRiskAssessment(session, riskAssessment);
                    break;

                case "getTrendAlerts":
                    if (!"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求趋势预警", user.getId());
                        return;
                    }

                    // 解析请求参数
                    LocalDateTime trendStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime trendEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod trendPeriod = createPeriod(trendStartTime, trendEndTime);
                    Double threshold = (Double) request.getOrDefault("threshold", 0.2);

                    // 获取系统统计数据
                    SystemTimeoutStatistics trendStats = globalTimeoutStatisticsService.getSystemStatistics(trendPeriod);

                    // 筛选显著变化的趋势
                    List<TimeoutTrend> significantTrends = trendStats.trends().stream()
                            .filter(trend -> Math.abs(trend.changeRate()) >= threshold)
                            .filter(TimeoutTrend::isSignificantChange)
                            .collect(Collectors.toList());

                    // 发送趋势预警数据
                    sendTrendAlerts(session, significantTrends);
                    break;

                case "getRegionAnalysis":
                    if (!"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求区域分析", user.getId());
                        return;
                    }

                    // 解析请求参数
                    LocalDateTime regionStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime regionEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod regionPeriod = createPeriod(regionStartTime, regionEndTime);

                    // 获取系统统计数据
                    SystemTimeoutStatistics regionStats = globalTimeoutStatisticsService.getSystemStatistics(regionPeriod);

                    // 获取区域统计信息
                    RegionStatistics regions = regionStats.regionStatistics();

                    // 构建区域分析数据
                    Map<String, Object> regionAnalysis = new HashMap<>();
                    regionAnalysis.put("timeoutCounts", regions.timeoutCounts());
                    regionAnalysis.put("timeoutRates", regions.timeoutRates());
                    regionAnalysis.put("highRiskRegions", regions.highRiskRegions());
                    regionAnalysis.put("mostTimeoutRegion", regions.getMostTimeoutRegion());

                    // 发送区域分析数据
                    sendRegionAnalysis(session, regionAnalysis);
                    break;

                case "getTimeAnalysis":
                    if (!"admin".equals(user.getUserGroup())) {
                        logger.warn("非管理员用户 {} 尝试请求时间分析", user.getId());
                        return;
                    }

                    // 解析请求参数
                    LocalDateTime timeStartTime = parseLocalDateTime(request.get("startTime"));
                    LocalDateTime timeEndTime = parseLocalDateTime(request.get("endTime"));
                    StatisticsPeriod timePeriod = createPeriod(timeStartTime, timeEndTime);

                    // 获取系统统计数据
                    SystemTimeoutStatistics timeStats = globalTimeoutStatisticsService.getSystemStatistics(timePeriod);

                    // 获取时间分布信息
                    TimeDistribution timeDistribution = timeStats.timeDistribution();

                    // 获取高峰时段
                    List<Integer> peakHours = timeDistribution.getPeakHours();

                    // 构建时间分析数据
                    Map<String, Object> timeAnalysis = new HashMap<>();
                    timeAnalysis.put("hourlyDistribution", timeDistribution.hourlyDistribution());
                    timeAnalysis.put("weekdayDistribution", timeDistribution.weekdayDistribution());
                    timeAnalysis.put("peakHours", peakHours);
                    timeAnalysis.put("highRiskHour", timeStats.getHighRiskHour());

                    // 发送时间分析数据
                    sendTimeAnalysis(session, timeAnalysis);
                    break;

                case "subscribeAlerts":
                    // 订阅实时警报
                    Boolean enableAlerts = (Boolean) request.getOrDefault("enable", true);

                    // 根据用户组设置不同的订阅级别
                    if ("admin".equals(user.getUserGroup())) {
                        session.getAttributes().put("alertSubscription", "admin");
                        logger.info("管理员 {} 已{}订阅实时警报", userId, enableAlerts ? "" : "取消");
                    } else {
                        session.getAttributes().put("alertSubscription", "user");
                        logger.info("用户 {} 已{}订阅实时警报", userId, enableAlerts ? "" : "取消");
                    }

                    // 发送订阅确认
                    sendAlertSubscriptionConfirmation(session, enableAlerts);
                    break;

                // 新增：处理获取超时报告请求
                case "getTimeoutReport":
                    handleTimeoutReportRequest(session, user, request);
                    break;

                // 新增：处理获取建议请求
                case "getRecommendations":
                    handleRecommendationsRequest(session, user, request);
                    break;

                default:
                    logger.warn("未知的WebSocket命令: {}", command);
                    sendErrorMessage(session, "未知命令: " + command);
            }
        } catch (Exception e) {
            logger.error("处理WebSocket消息时发生错误", e);
            sendErrorMessage(session, "处理请求时发生错误: " + e.getMessage());
        }
    }

    /**
     * 新增：处理获取超时报告请求
     */
    private void handleTimeoutReportRequest(WebSocketSession session, User user, Map<String, Object> request) throws IOException {
        logger.info("处理获取超时报告请求，用户: {}", user.getId());

        // 检查权限（仅管理员可访问）
        if (!"admin".equals(user.getUserGroup())) {
            logger.warn("非管理员用户 {} 尝试请求超时报告", user.getId());
            sendErrorMessage(session, "无权限获取超时报告");
            return;
        }

        // 检查是否需要特定时间段的报告
        Boolean getLatest = (Boolean) request.getOrDefault("getLatest", true);

        if (getLatest) {
            // 获取最新的报告
            GlobalTimeoutReport report = globalTimeoutStatisticsService.getLatestReport();
            sendTimeoutReport(session, report);
        } else {
            // 解析时间范围
            LocalDateTime startTime = parseLocalDateTime(request.get("startTime"));
            LocalDateTime endTime = parseLocalDateTime(request.get("endTime"));

            // 默认30天
            if (startTime == null) {
                startTime = LocalDateTime.now().minusDays(30);
            }
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            // 获取指定时间段的报告
            List<GlobalTimeoutReport> reports = globalTimeoutStatisticsService.getReportsByPeriod(startTime, endTime);
            sendTimeoutReports(session, reports);
        }
    }

    /**
     * 新增：处理获取建议请求
     */
    private void handleRecommendationsRequest(WebSocketSession session, User user, Map<String, Object> request) throws IOException {
        logger.info("处理获取建议请求，用户: {}", user.getId());

        // 解析时间范围
        LocalDateTime startTime = parseLocalDateTime(request.get("startTime"));
        LocalDateTime endTime = parseLocalDateTime(request.get("endTime"));
        StatisticsPeriod period = createPeriod(startTime, endTime);

        List<String> recommendations;

        // 根据用户角色获取不同的建议
        if ("admin".equals(user.getUserGroup())) {
            // 获取系统级建议
            GlobalTimeoutReport latestReport = globalTimeoutStatisticsService.getLatestReport();
            if (latestReport != null && latestReport.getRecommendations() != null) {
                recommendations = latestReport.getRecommendations();
            } else {
                SystemTimeoutStatistics systemStats = globalTimeoutStatisticsService.getSystemStatistics(period);
                List<UserTimeoutRanking> userRankings = globalTimeoutStatisticsService
                        .getUserTimeoutRanking(period, 10, "timeoutRate", false);
                recommendations = globalTimeoutStatisticsService.generateRecommendationsFromRankings(
                        systemStats, userRankings);
            }
        } else {
            // 获取用户级建议
            recommendations = globalTimeoutStatisticsService.generateUserRecommendations(user, period);
        }

        // 发送建议数据
        sendRecommendations(session, recommendations, period);
    }

    /**
     * 新增：发送超时报告
     */
    private void sendTimeoutReport(WebSocketSession session, GlobalTimeoutReport report) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "timeoutReport",
                "data", report,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        logger.debug("已发送最新超时报告");
    }

    /**
     * 新增：发送超时报告列表
     */
    private void sendTimeoutReports(WebSocketSession session, List<GlobalTimeoutReport> reports) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "timeoutReports",
                "data", reports,
                "count", reports.size(),
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        logger.debug("已发送 {} 份超时报告", reports.size());
    }

    /**
     * 新增：发送建议数据
     */
    private void sendRecommendations(WebSocketSession session, List<String> recommendations, StatisticsPeriod period) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "recommendations",
                "recommendations", recommendations,
                "period", Map.of(
                        "startTime", period.startTime(),
                        "endTime", period.endTime()
                ),
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        logger.debug("已发送 {} 条建议", recommendations.size());
    }

    @NotNull
    private static Map<String, Object> getRiskAssessment(UserTimeoutStatistics userStats) {
        Map<String, Object> riskAssessment = new HashMap<>();
        riskAssessment.put("isHighRisk", userStats.isHighRisk());
        riskAssessment.put("overallTimeoutRate", userStats.getOverallTimeoutRate());
        riskAssessment.put("timeoutCount", userStats.getTimeoutCount());

        // 针对每种服务类型提供风险评估
        Map<String, Object> serviceRiskAssessment = new HashMap<>();
        for (Map.Entry<String, ServiceGroupStatistics> entry : userStats.serviceStatistics().entrySet()) {
            Map<String, Object> serviceRisk = getServiceRisk(entry);

            serviceRiskAssessment.put(entry.getKey(), serviceRisk);
        }

        riskAssessment.put("serviceRisks", serviceRiskAssessment);
        return riskAssessment;
    }

    @NotNull
    private static Map<String, Object> getServiceRisk(Map.Entry<String, ServiceGroupStatistics> entry) {
        ServiceGroupStatistics serviceStats = entry.getValue();

        Map<String, Object> serviceRisk = new HashMap<>();
        serviceRisk.put("needsSpecialAttention", serviceStats.needsSpecialAttention());
        serviceRisk.put("significantDelayCount", serviceStats.getSignificantDelayCount());

        // 提取风险指标
        ServiceRiskMetrics riskMetrics = serviceStats.riskMetrics();
        serviceRisk.put("riskLevel", riskMetrics.getRiskLevel().name());
        serviceRisk.put("needsIntervention", riskMetrics.needsIntervention());
        serviceRisk.put("recommendedAction", riskMetrics.getRecommendedAction());
        return serviceRisk;
    }

    /**
     * 发送风险分析数据
     */
    private void sendRiskAnalysis(WebSocketSession session, Map<String, Object> riskAnalysis) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "riskAnalysis",
                "data", riskAnalysis,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送用户风险评估数据
     */
    private void sendUserRiskAssessment(WebSocketSession session, Map<String, Object> riskAssessment) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "userRiskAssessment",
                "data", riskAssessment,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送趋势预警数据
     */
    private void sendTrendAlerts(WebSocketSession session, List<TimeoutTrend> trends) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "trendAlerts",
                "data", trends,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送区域分析数据
     */
    private void sendRegionAnalysis(WebSocketSession session, Map<String, Object> regionAnalysis) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "regionAnalysis",
                "data", regionAnalysis,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送时间分析数据
     */
    private void sendTimeAnalysis(WebSocketSession session, Map<String, Object> timeAnalysis) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "timeAnalysis",
                "data", timeAnalysis,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送警报订阅确认
     */
    private void sendAlertSubscriptionConfirmation(WebSocketSession session, boolean enabled) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "alertSubscription",
                "status", enabled ? "enabled" : "disabled",
                "message", enabled ? "已成功订阅实时警报" : "已取消订阅实时警报",
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 广播风险警报
     */
    public void broadcastRiskAlert(Map<String, Object> alertData) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "riskAlert",
                    "data", alertData,
                    "timestamp", LocalDateTime.now().toString()
            );

            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            // 向所有订阅的管理员发送警报
            for (WebSocketSession session : broadcastService.getAdminSessions()) {
                if (session.isOpen() && "admin".equals(session.getAttributes().get("alertSubscription"))) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        logger.error("向管理员发送风险警报失败", e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("序列化风险警报消息失败", e);
        }
    }

    /**
     * 向指定用户发送风险警报
     */
    public void sendUserRiskAlert(Long userId, Map<String, Object> alertData) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "userRiskAlert",
                    "data", alertData,
                    "timestamp", LocalDateTime.now().toString()
            );

            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            // 获取用户的所有会话
            Set<WebSocketSession> sessions = broadcastService.getUserSessions(userId);
            if (sessions != null) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen() && "user".equals(session.getAttributes().get("alertSubscription"))) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("向用户 {} 发送风险警报失败", userId, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("序列化用户风险警报消息失败", e);
        }
    }
    /**
     * 从握手请求中提取访问令牌
     */
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

    /**
     * 从URL中提取令牌
     */
    private String extractTokenFromUrl(URI uri) {
        if (uri != null && uri.getQuery() != null) {
            String[] pairs = uri.getQuery().split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    if ("token".equals(key)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 发送初始数据
     */
    private void sendInitialData(WebSocketSession session, User user, String type) {
        try {
            logger.info("正在准备发送初始数据，类型: {}, 用户: {}", type, user.getId());
            StatisticsPeriod period = StatisticsPeriod.today();

            // 先发送一个简单的确认消息测试连接
            try {
                Map<String, Object> testMessage = Map.of(
                        "type", "INIT_TEST",
                        "message", "开始加载初始数据",
                        "timestamp", LocalDateTime.now().toString()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(testMessage)));
                logger.info("测试消息发送成功");
            } catch (Exception e) {
                logger.error("发送测试消息失败", e);
                throw e;
            }

            // 获取并发送相应的统计数据
            if ("user".equals(type)) {
                logger.info("获取用户统计数据");
                UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatistics(user, period);
                logger.info("用户统计数据获取成功，准备发送");
                sendUserStatistics(session, statistics);

                // 新增：发送用户级别的建议数据
                List<String> recommendations = globalTimeoutStatisticsService.generateUserRecommendations(user, period);
                sendRecommendations(session, recommendations, period);
            } else if ("system".equals(type)) {
                logger.info("获取系统统计数据");
                SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);
                logger.info("系统统计数据获取成功，准备发送");
                sendSystemStatistics(session, statistics);

                // 新增：发送系统级别的报告和建议数据
                if ("admin".equals(user.getUserGroup())) {
                    GlobalTimeoutReport report = globalTimeoutStatisticsService.getLatestReport();
                    sendTimeoutReport(session, report);

                    List<String> recommendations = report.getRecommendations();
                    sendRecommendations(session, recommendations, period);
                }
            } else if ("global".equals(type)) {
                logger.info("获取全局统计数据");
                // 使用相同的系统统计数据，保持一致性
                SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);
                logger.info("全局统计数据获取成功，准备发送");
                sendGlobalStatistics(session, statistics);
            }
            logger.info("初始数据发送完成");
        } catch (Exception e) {
            logger.error("发送初始数据时出错: {}", e.getMessage(), e);
            try {
                // 发送错误消息给客户端
                Map<String, Object> errorMessage = Map.of(
                        "type", "ERROR",
                        "message", "获取初始数据失败: " + e.getMessage(),
                        "timestamp", LocalDateTime.now().toString()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
            } catch (IOException ex) {
                logger.error("发送错误消息失败", ex);
            }
        }
    }

    /**
     * 发送用户统计数据
     */
    private void sendUserStatistics(WebSocketSession session, UserTimeoutStatistics statistics) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "user",
                "data", statistics,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送系统统计数据
     */
    private void sendSystemStatistics(WebSocketSession session, SystemTimeoutStatistics statistics) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "system",
                "data", statistics,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送全局统计数据
     */
    private void sendGlobalStatistics(WebSocketSession session, SystemTimeoutStatistics statistics) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "global",
                "data", statistics,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送排行榜数据
     */
    private void sendRankingData(WebSocketSession session, List<?> rankings, String sortBy, String direction) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "ranking",
                "data", rankings,
                "sortBy", sortBy,
                "direction", direction,
                "timestamp", LocalDateTime.now().toString()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                            "type", "ERROR",
                            "message", errorMessage,
                            "timestamp", LocalDateTime.now().toString()
                    )
            )));
        } catch (IOException e) {
            logger.error("发送错误消息时发生错误", e);
        }
    }

    /**
     * 定时广播统计更新
     */
    @Scheduled(fixedRate = 30000) // 每30秒执行一次
    public void broadcastUpdates() {
        try {
            int adminCount = broadcastService.getActiveAdminSessionCount();
            int userCount = broadcastService.getActiveUserSessionCount();

            if (adminCount + userCount == 0) {
                return; // 没有活跃的WebSocket连接
            }

            StatisticsPeriod period = StatisticsPeriod.today();

            // 如果有管理员连接，则广播系统统计
            if (adminCount > 0) {
                // 使用合并后的统计服务获取系统统计数据
                SystemTimeoutStatistics systemStats = globalTimeoutStatisticsService.getSystemStatistics(period);

                // 广播系统统计和全局统计（数据相同）
                broadcastService.broadcastSystemStatisticsUpdate(systemStats);
                broadcastService.broadcastGlobalStatisticsUpdate(systemStats);

                logger.debug("已向管理员广播系统和全局超时统计更新");
            }
        } catch (Exception e) {
            logger.error("广播超时统计更新时发生错误", e);
        }
    }

    /**
     * 解析查询参数
     */
    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new ConcurrentHashMap<>();

        if (uri != null && uri.getQuery() != null) {
            String[] pairs = uri.getQuery().split("&");

            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    params.put(key, value);
                }
            }
        }

        return params;
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseLocalDateTime(Object dateTimeStr) {
        if (dateTimeStr == null) {
            return null;
        }

        try {
            String strDateTime = dateTimeStr.toString();

            // 尝试直接解析为LocalDateTime
            try {
                return LocalDateTime.parse(strDateTime);
            } catch (Exception e) {
                // 如果包含Z或时区偏移信息，尝试解析为ZonedDateTime或OffsetDateTime
                if (strDateTime.endsWith("Z") || strDateTime.contains("+") || strDateTime.contains("-")) {
                    try {
                        // 尝试解析为ZonedDateTime
                        ZonedDateTime zdt = ZonedDateTime.parse(strDateTime);
                        // 转换为系统默认时区的LocalDateTime
                        return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                    } catch (Exception e2) {
                        // 尝试解析为OffsetDateTime
                        OffsetDateTime odt = OffsetDateTime.parse(strDateTime);
                        // 转换为系统默认时区的LocalDateTime
                        return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                    }
                }
                // 其他格式解析失败
                throw e;
            }
        } catch (Exception e) {
            logger.warn("解析日期时间字符串失败: {}", dateTimeStr);
            return null;
        }
    }

    /**
     * 创建统计时间段
     */
    private StatisticsPeriod createPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null && endTime == null) {
            // 默认使用今天
            return StatisticsPeriod.today();
        } else if (startTime == null) {
            // 如果只有结束时间，则使用结束时间的前7天作为开始时间
            startTime = endTime.minusDays(7);
        } else if (endTime == null) {
            // 如果只有开始时间，则使用当前时间作为结束时间
            endTime = LocalDateTime.now();
        }

        return new StatisticsPeriod(startTime, endTime);
    }
}