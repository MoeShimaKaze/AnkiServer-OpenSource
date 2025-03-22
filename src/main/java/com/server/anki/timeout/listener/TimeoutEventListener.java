package com.server.anki.timeout.listener;

import com.server.anki.timeout.event.TimeoutEvent;
import com.server.anki.timeout.service.GlobalTimeoutStatisticsService;
import com.server.anki.timeout.service.TimeoutRiskAnalysisService;
import com.server.anki.timeout.model.StatisticsPeriod;
import com.server.anki.timeout.model.SystemTimeoutStatistics;
import com.server.anki.timeout.model.UserTimeoutStatistics;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.websocket.service.TimeoutStatisticsBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 超时事件监听器
 * 负责监听并处理系统中的超时事件，更新相关统计数据并进行广播
 */
@Component
public class TimeoutEventListener {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutEventListener.class);

    @Autowired
    private GlobalTimeoutStatisticsService globalTimeoutStatisticsService;

    @Autowired
    private TimeoutStatisticsBroadcastService broadcastService;

    @Autowired
    private UserService userService;

    @Autowired
    private TimeoutRiskAnalysisService riskAnalysisService;

    /**
     * 异步处理超时事件
     * 根据事件类型更新相关统计数据并广播
     *
     * @param event 超时事件
     */
    @Async
    @EventListener
    public void handleTimeoutEvent(TimeoutEvent event) {
        try {
            logger.debug("处理超时事件: 类型={}, 用户ID={}", event.getTimeoutType(), event.getUserId());

            // 获取当前的统计时间段（今天）
            StatisticsPeriod todayPeriod = StatisticsPeriod.today();

            // 如果涉及特定用户，更新并广播该用户的统计
            if (event.getUserId() != null) {
                User user = userService.getUserById(event.getUserId());
                if (user != null) {
                    // 获取用户统计数据
                    UserTimeoutStatistics userStats = globalTimeoutStatisticsService.getUserStatistics(user, todayPeriod);
                    // 广播用户统计更新
                    broadcastService.broadcastUserStatisticsUpdate(user.getId(), userStats);
                    logger.debug("已广播用户 {} 的超时统计更新", user.getId());
                } else {
                    logger.warn("无法找到用户ID为 {} 的用户", event.getUserId());
                }
            }

            // 更新并广播系统统计数据（包含所有类型订单统计）
            SystemTimeoutStatistics systemStats = globalTimeoutStatisticsService.getSystemStatistics(todayPeriod);
            broadcastService.broadcastSystemStatisticsUpdate(systemStats);
            logger.debug("已广播系统超时统计更新");

            // 由于系统统计现在已经包含所有订单类型，不再需要单独广播全局统计
            // 但为保持兼容性，仍使用相同数据进行广播
            broadcastService.broadcastGlobalStatisticsUpdate(systemStats);
            logger.debug("已广播全局统计更新（与系统统计相同）");

            // 分析事件风险并在必要时发送警报
            riskAnalysisService.analyzeEventRisk(event.getTimeoutType(), event.getUserId());

            logger.info("已完成超时事件的统计更新、广播和风险分析");
        } catch (Exception e) {
            logger.error("处理超时事件时发生错误: {}", e.getMessage(), e);
        }
    }
}