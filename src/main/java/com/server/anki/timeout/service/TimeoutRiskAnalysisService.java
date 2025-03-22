package com.server.anki.timeout.service;

import com.server.anki.timeout.model.*;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.websocket.TimeoutStatisticsWebSocketHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超时风险分析服务
 * 负责分析系统和用户的超时风险，并发送相应的警报
 */
@Service
public class TimeoutRiskAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutRiskAnalysisService.class);

    @Autowired
    private GlobalTimeoutStatisticsService statisticsService;

    @Autowired
    private UserService userService;

    @Autowired
    private TimeoutStatisticsWebSocketHandler webSocketHandler;

    /**
     * 定时分析系统风险并发送警报
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void analyzeSystemRisks() {
        try {
            logger.debug("开始分析系统风险...");

            // 获取今日统计
            StatisticsPeriod period = StatisticsPeriod.today();
            SystemTimeoutStatistics systemStats = statisticsService.getSystemStatistics(period);

            // 分析高风险状态
            if (systemStats.isHighRiskState()) {
                sendSystemRiskAlert(systemStats);
            }

            // 分析风险趋势
            analyzeTrends(systemStats);

            // 分析区域风险
            analyzeRegionRisks(systemStats);

            logger.debug("系统风险分析完成");
        } catch (Exception e) {
            logger.error("分析系统风险时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时分析用户风险并发送警报
     */
    @Scheduled(fixedRate = 600000) // 每10分钟执行一次
    public void analyzeUserRisks() {
        try {
            logger.debug("开始分析用户风险...");

            // 获取今日统计
            StatisticsPeriod period = StatisticsPeriod.today();

            // 获取高风险用户
            List<UserTimeoutStatistics> highRiskUsers = statisticsService.getTopTimeoutUsers(period, 0);

            // 过滤出真正的高风险用户
            List<UserTimeoutStatistics> filteredUsers = highRiskUsers.stream()
                    .filter(UserTimeoutStatistics::isHighRisk)
                    .toList();

            logger.info("检测到 {} 个高风险用户", filteredUsers.size());

            // 为每个高风险用户发送警报
            for (UserTimeoutStatistics userStats : filteredUsers) {
                sendUserRiskAlert(userStats);
            }

            logger.debug("用户风险分析完成");
        } catch (Exception e) {
            logger.error("分析用户风险时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 响应超时事件分析风险
     * 该方法可以由TimeoutEventListener调用
     */
    public void analyzeEventRisk(String timeoutType, Long userId) {
        try {
            // 用户相关风险分析
            if (userId != null) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    StatisticsPeriod period = StatisticsPeriod.today();
                    UserTimeoutStatistics userStats = statisticsService.getUserStatistics(user, period);

                    // 检查是否为新的高风险用户
                    if (userStats.isHighRisk()) {
                        // 发送用户风险警报
                        sendUserRiskAlert(userStats);
                    }
                }
            }

            // 系统级风险分析
            // 这里可以根据timeoutType进行特定类型的分析
            analyzeSystemRisks();
        } catch (Exception e) {
            logger.error("分析事件风险时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送系统风险警报
     * 已修复类型兼容性问题
     */
    private void sendSystemRiskAlert(SystemTimeoutStatistics systemStats) {
        // 构建系统风险警报数据
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "SYSTEM_HIGH_RISK");
        alertData.put("highRiskRegion", systemStats.getHighRiskRegion());
        alertData.put("highRiskHour", systemStats.getHighRiskHour());
        alertData.put("timestamp", LocalDateTime.now().toString());

        // 添加风险模式信息 - 使用显式类型的ArrayList和HashMap而非Map.of()
        List<Map<String, Object>> riskPatterns = new ArrayList<>();

        for (HighRiskPattern pattern : systemStats.riskPatterns()) {
            if (pattern.isUrgent()) {
                Map<String, Object> patternMap = new HashMap<>();
                patternMap.put("pattern", pattern.pattern());
                patternMap.put("description", pattern.description());
                patternMap.put("riskLevel", pattern.riskLevel());
                riskPatterns.add(patternMap);
            }
        }

        alertData.put("riskPatterns", riskPatterns);
        alertData.put("message", "系统检测到高风险状态，建议立即介入");

        // 广播系统风险警报
        webSocketHandler.broadcastRiskAlert(alertData);

        logger.info("已广播系统风险警报");
    }

    /**
     * 发送用户风险警报
     */
    private void sendUserRiskAlert(UserTimeoutStatistics userStats) {
        // 构建用户风险警报数据
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("userId", userStats.userId());
        alertData.put("timeoutRate", userStats.getOverallTimeoutRate());
        alertData.put("timeoutCount", userStats.getTimeoutCount());
        alertData.put("alertType", "HIGH_RISK_USER");
        alertData.put("timestamp", LocalDateTime.now().toString());

        // 添加服务类型分析
        Map<String, Object> serviceAnalysis = new HashMap<>();
        for (Map.Entry<String, ServiceGroupStatistics> entry : userStats.serviceStatistics().entrySet()) {
            if (entry.getValue().needsSpecialAttention()) {
                Map<String, Object> serviceInfo = new HashMap<>();
                serviceInfo.put("riskLevel", entry.getValue().riskMetrics().getRiskLevel().toString());
                serviceInfo.put("recommendation", entry.getValue().riskMetrics().getRecommendedAction());

                serviceAnalysis.put(entry.getKey(), serviceInfo);
            }
        }

        alertData.put("serviceAnalysis", serviceAnalysis);
        alertData.put("message", "您的超时率较高，可能影响接单权限，请注意改善");

        // 发送用户风险警报
        webSocketHandler.sendUserRiskAlert(userStats.userId(), alertData);

        logger.info("已向用户 {} 发送风险警报", userStats.userId());
    }

    /**
     * 分析超时趋势
     * 已修复类型兼容性问题
     */
    private void analyzeTrends(SystemTimeoutStatistics systemStats) {
        List<TimeoutTrend> significantTrends = systemStats.trends().stream()
                .filter(TimeoutTrend::isSignificantChange)
                .toList();

        if (!significantTrends.isEmpty()) {
            // 构建趋势警报数据
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("alertType", "SIGNIFICANT_TREND_CHANGE");
            alertData.put("trendCount", significantTrends.size());
            alertData.put("timestamp", LocalDateTime.now().toString());

            // 添加趋势详情 - 使用显式类型的ArrayList和HashMap
            List<Map<String, Object>> trendDetails = getTrendDetails(significantTrends);

            alertData.put("trends", trendDetails);

            // 生成消息
            String message = "检测到超时率显著变化，请关注详情";
            for (TimeoutTrend trend : significantTrends) {
                if (trend.isIncreasing()) {
                    message = "检测到超时率显著上升趋势，请及时关注";
                    break;
                }
            }

            alertData.put("message", message);

            // 广播趋势警报
            webSocketHandler.broadcastRiskAlert(alertData);

            logger.info("已广播趋势变化警报");
        }
    }

    @NotNull
    private static List<Map<String, Object>> getTrendDetails(List<TimeoutTrend> significantTrends) {
        List<Map<String, Object>> trendDetails = new ArrayList<>();

        for (TimeoutTrend trend : significantTrends) {
            Map<String, Object> trendMap = new HashMap<>();
            trendMap.put("timeFrame", trend.timeFrame());
            trendMap.put("description", trend.getTrendDescription());
            trendMap.put("changeRate", trend.changeRate());
            trendMap.put("isIncreasing", trend.isIncreasing());
            trendDetails.add(trendMap);
        }
        return trendDetails;
    }

    /**
     * 分析区域风险
     */
    private void analyzeRegionRisks(SystemTimeoutStatistics systemStats) {
        List<String> highRiskRegions = systemStats.regionStatistics().highRiskRegions();

        if (!highRiskRegions.isEmpty()) {
            // 构建区域风险警报数据
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("alertType", "HIGH_RISK_REGIONS");
            alertData.put("regions", highRiskRegions);
            alertData.put("timestamp", LocalDateTime.now().toString());

            // 添加区域超时率
            Map<String, Object> regionRates = new HashMap<>();
            for (String region : highRiskRegions) {
                regionRates.put(region, systemStats.regionStatistics().getRegionTimeoutRate(region));
            }

            alertData.put("regionRates", regionRates);
            alertData.put("message", "检测到" + highRiskRegions.size() + "个高风险区域，建议重点关注");

            // 广播区域风险警报
            webSocketHandler.broadcastRiskAlert(alertData);

            logger.info("已广播区域风险警报");
        }
    }
}