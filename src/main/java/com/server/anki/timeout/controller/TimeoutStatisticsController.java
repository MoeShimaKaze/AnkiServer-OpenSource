package com.server.anki.timeout.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.timeout.model.*;
import com.server.anki.timeout.service.GlobalTimeoutStatisticsService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 超时统计控制器
 * 提供所有与超时统计相关的API端点
 */
@RestController
@RequestMapping("/api/timeout-statistics")
public class TimeoutStatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutStatisticsController.class);

    @Autowired
    private GlobalTimeoutStatisticsService globalTimeoutStatisticsService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户的超时统计
     */
    @GetMapping("/user")
    public ResponseEntity<?> getUserStatistics(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatistics(user, period);

        logger.info("用户 {} 获取了个人超时统计数据", user.getId());
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取当前用户的历史超时统计记录
     * 支持指定不同的时间段进行查询
     */
    @GetMapping("/history")
    public ResponseEntity<?> getUserTimeoutHistory(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 如果没有指定时间范围，使用最近days天的数据
        StatisticsPeriod period;
        if (startTime == null && endTime == null) {
            period = StatisticsPeriod.lastNDays(days);
        } else {
            period = createPeriod(startTime, endTime);
        }

        UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatisticsById(user.getId(), period);

        logger.info("用户 {} 获取了个人历史超时统计数据，时间段：{} 至 {}",
                user.getId(), period.startTime(), period.endTime());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("statistics", statistics);
        responseData.put("period", Map.of(
                "startTime", period.startTime(),
                "endTime", period.endTime()
        ));
        responseData.put("userId", user.getId());
        responseData.put("username", user.getUsername());

        return ResponseEntity.ok(responseData);
    }

    /**
     * 获取当前用户特定类型订单的超时详情
     */
    @GetMapping("/details/{orderType}")
    public ResponseEntity<?> getUserTimeoutDetails(
            @PathVariable String orderType,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatisticsById(user.getId(), period);

        // 检查请求的订单类型是否存在
        if (!statistics.serviceStatistics().containsKey(orderType)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到类型为 " + orderType + " 的超时统计数据"));
        }

        ServiceGroupStatistics typeStats = statistics.serviceStatistics().get(orderType);

        Map<String, Object> detailsResponse = new HashMap<>();
        detailsResponse.put("orderType", orderType);
        detailsResponse.put("statistics", typeStats.statistics());
        detailsResponse.put("incidents", typeStats.incidents());
        detailsResponse.put("riskMetrics", typeStats.riskMetrics());
        detailsResponse.put("period", Map.of(
                "startTime", period.startTime(),
                "endTime", period.endTime()
        ));

        logger.info("用户 {} 获取了 {} 类型订单的超时详情", user.getId(), orderType);
        return ResponseEntity.ok(detailsResponse);
    }

    /**
     * 比较不同时间段的超时统计数据
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareTimeoutStatistics(
            @RequestParam() LocalDateTime periodOneStart,
            @RequestParam() LocalDateTime periodOneEnd,
            @RequestParam() LocalDateTime periodTwoStart,
            @RequestParam() LocalDateTime periodTwoEnd,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 创建两个时间段
        StatisticsPeriod periodOne = new StatisticsPeriod(periodOneStart, periodOneEnd);
        StatisticsPeriod periodTwo = new StatisticsPeriod(periodTwoStart, periodTwoEnd);

        // 获取两个时间段的统计数据
        UserTimeoutStatistics statsOne = globalTimeoutStatisticsService.getUserStatisticsById(user.getId(), periodOne);
        UserTimeoutStatistics statsTwo = globalTimeoutStatisticsService.getUserStatisticsById(user.getId(), periodTwo);

        Map<String, Object> comparisonResult = new HashMap<>();
        comparisonResult.put("periodOne", Map.of(
                "startTime", periodOne.startTime(),
                "endTime", periodOne.endTime(),
                "statistics", statsOne
        ));
        comparisonResult.put("periodTwo", Map.of(
                "startTime", periodTwo.startTime(),
                "endTime", periodTwo.endTime(),
                "statistics", statsTwo
        ));

        // 计算变化率
        Map<String, Object> changes = getStringObjectMap(statsOne, statsTwo);
        comparisonResult.put("changes", changes);

        logger.info("用户 {} 比较了两个时间段的超时统计数据", user.getId());
        return ResponseEntity.ok(comparisonResult);
    }

    @NotNull
    private Map<String, Object> getStringObjectMap(UserTimeoutStatistics statsOne, UserTimeoutStatistics statsTwo) {
        Map<String, Object> changes = new HashMap<>();

        // 总超时费用变化
        double feeChange = calculatePercentageChange(
                statsOne.totalTimeoutFees().doubleValue(),
                statsTwo.totalTimeoutFees().doubleValue()
        );

        // 超时率变化
        double rateChange = calculatePercentageChange(
                statsOne.getOverallTimeoutRate(),
                statsTwo.getOverallTimeoutRate()
        );

        changes.put("timeoutFeeChange", feeChange);
        changes.put("timeoutRateChange", rateChange);
        return changes;
    }

    /**
     * 获取系统超时统计 (仅管理员可访问)
     */
    @GetMapping("/system")
    public ResponseEntity<?> getSystemStatistics(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问系统超时统计", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

        logger.info("管理员 {} 获取了系统超时统计数据", user.getId());
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取全局超时统计 (仅管理员可访问)
     * 注意: 在合并后的设计中，全局统计和系统统计使用相同的数据源
     */
    @GetMapping("/global")
    public ResponseEntity<?> getGlobalStatistics(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问全局超时统计", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        // 使用系统统计数据，现在系统统计已包含所有订单类型
        SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

        logger.info("管理员 {} 获取了全局超时统计数据", user.getId());
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取指定用户的超时统计 (仅管理员可访问)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserStatisticsById(
            @PathVariable Long userId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User adminUser = authenticationService.getAuthenticatedUser(request, response);
        if (adminUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员或者查询自己的统计
        if (!adminUser.getId().equals(userId) && !userService.isAdminUser(adminUser)) {
            logger.warn("用户 {} 尝试访问用户 {} 的超时统计", adminUser.getId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 获取目标用户
        User targetUser = userService.getUserById(userId);
        if (targetUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到ID为 " + userId + " 的用户"));
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatistics(targetUser, period);

        logger.info("用户 {} 获取了用户 {} 的超时统计数据", adminUser.getId(), userId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 获取超时统计概览 (用于仪表盘)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStatistics(
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> dashboardData = new HashMap<>();

        if (userService.isAdminUser(user)) {
            // 管理员看到系统概览
            StatisticsPeriod todayPeriod = StatisticsPeriod.today();
            StatisticsPeriod weekPeriod = StatisticsPeriod.lastNDays(7);

            SystemTimeoutStatistics todayStats = globalTimeoutStatisticsService.getSystemStatistics(todayPeriod);
            SystemTimeoutStatistics weekStats = globalTimeoutStatisticsService.getSystemStatistics(weekPeriod);

            dashboardData.put("todayStatistics", todayStats);
            dashboardData.put("weekStatistics", weekStats);
            dashboardData.put("isAdmin", true);

            logger.info("管理员 {} 获取了超时统计仪表盘数据", user.getId());
        } else {
            // 普通用户看到个人概览
            StatisticsPeriod todayPeriod = StatisticsPeriod.today();
            StatisticsPeriod weekPeriod = StatisticsPeriod.lastNDays(7);

            UserTimeoutStatistics todayStats = globalTimeoutStatisticsService.getUserStatistics(user, todayPeriod);
            UserTimeoutStatistics weekStats = globalTimeoutStatisticsService.getUserStatistics(user, weekPeriod);

            dashboardData.put("todayStatistics", todayStats);
            dashboardData.put("weekStatistics", weekStats);
            dashboardData.put("isAdmin", false);

            logger.info("用户 {} 获取了个人超时统计仪表盘数据", user.getId());
        }

        return ResponseEntity.ok(dashboardData);
    }

    /**
     * 获取用户超时排行榜
     * 支持分页、排序和时间范围筛选
     */
    @GetMapping("/ranking")
    public ResponseEntity<?> getUserTimeoutRanking(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timeoutRate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问超时排行榜", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);
        boolean ascending = "asc".equalsIgnoreCase(direction);

        try {
            // 使用合并后的服务获取排行数据，包含所有类型订单
            List<UserTimeoutRanking> allRankings = globalTimeoutStatisticsService.getUserTimeoutRanking(
                    period, 0, sortBy, ascending);

            // 手动实现分页
            int totalItems = allRankings.size();
            int totalPages = (int) Math.ceil((double) totalItems / size);

            // 检查页码是否有效
            if (page < 0) {
                page = 0;
            } else if (page >= totalPages && totalItems > 0) {
                page = totalPages - 1;
            }

            // 计算分页范围
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalItems);

            // 提取当前页的数据
            List<UserTimeoutRanking> pagedRankings =
                    fromIndex < totalItems ? allRankings.subList(fromIndex, toIndex) : Collections.emptyList();

            // 构建分页响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", pagedRankings);
            responseData.put("page", page);
            responseData.put("size", size);
            responseData.put("totalItems", totalItems);
            responseData.put("totalPages", totalPages);
            responseData.put("sortBy", sortBy);
            responseData.put("direction", direction);
            responseData.put("startTime", period.startTime());
            responseData.put("endTime", period.endTime());

            logger.info("管理员 {} 获取了用户超时排行榜数据，共 {} 条记录", user.getId(), totalItems);
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取用户超时排行榜时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取排行榜数据失败: " + e.getMessage()));
        }
    }

    /**
     * 获取全局超时排行榜
     * 在合并后的设计中，该端点与/ranking端点返回相同数据
     */
    @GetMapping("/global-ranking")
    public ResponseEntity<?> getGlobalUserTimeoutRanking(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timeoutRate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 直接调用统一的排行榜端点
        return getUserTimeoutRanking(startTime, endTime, page, size, sortBy, direction, request, response);
    }

    /**
     * 获取排行榜详情，包含指定用户的详细统计信息
     */
    @GetMapping("/ranking/details")
    public ResponseEntity<?> getRankingDetails(
            @RequestParam List<Long> userIds,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问排行榜详情", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取指定用户的详细统计信息
            List<UserTimeoutStatistics> details = new ArrayList<>();
            for (Long userId : userIds) {
                User targetUser = userService.getUserById(userId);
                if (targetUser != null) {
                    UserTimeoutStatistics userStats = globalTimeoutStatisticsService.getUserStatistics(targetUser, period);
                    details.add(userStats);
                }
            }

            logger.info("管理员 {} 获取了 {} 个用户的超时排行详情", user.getId(), details.size());
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            logger.error("获取排行榜详情时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取排行榜详情失败: " + e.getMessage()));
        }
    }

    /**
     * 创建统计时间段
     */
    private StatisticsPeriod createPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null && endTime == null) {
            // 默认使用过去7天
            return StatisticsPeriod.lastNDays(7);
        } else if (startTime == null) {
            // 只有结束时间时，从结束时间前7天开始
            startTime = endTime.minusDays(7);
        } else if (endTime == null) {
            // 只有开始时间时，以当前时间结束
            endTime = LocalDateTime.now();
        }

        return new StatisticsPeriod(startTime, endTime);
    }

    /**
     * 计算百分比变化
     */
    private double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) {
            return newValue == 0 ? 0 : 100; // 避免除以零
        }
        return ((newValue - oldValue) / oldValue) * 100;
    }

    /**
     * 获取高风险用户列表
     * 根据超时率和频率识别可能需要关注的用户
     */
    @GetMapping("/high-risk-users")
    public ResponseEntity<?> getHighRiskUsers(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问高风险用户列表", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取高风险用户列表
            List<UserTimeoutStatistics> highRiskUsers = globalTimeoutStatisticsService.getTopTimeoutUsers(period, limit);

            // 过滤出符合高风险条件的用户
            List<UserTimeoutStatistics> filteredUsers = highRiskUsers.stream()
                    .filter(UserTimeoutStatistics::isHighRisk)
                    .collect(Collectors.toList());

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("users", filteredUsers);
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));
            responseData.put("totalCount", filteredUsers.size());

            logger.info("管理员 {} 获取了高风险用户列表，共 {} 条记录", user.getId(), filteredUsers.size());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取高风险用户列表时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取高风险用户列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取区域超时分析
     * 分析不同区域的超时情况，返回热点图数据
     */
    @GetMapping("/region-analysis")
    public ResponseEntity<?> getRegionAnalysis(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问区域超时分析", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取系统统计数据
            SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

            // 获取区域统计信息
            RegionStatistics regionStats = statistics.regionStatistics();

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("timeoutCounts", regionStats.timeoutCounts());
            responseData.put("timeoutRates", regionStats.timeoutRates());
            responseData.put("highRiskRegions", regionStats.highRiskRegions());
            responseData.put("mostTimeoutRegion", regionStats.getMostTimeoutRegion());
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));

            logger.info("管理员 {} 获取了区域超时分析数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取区域超时分析时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取区域超时分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取时间分布分析
     * 分析不同时间段的超时情况，返回时间热力图数据
     */
    @GetMapping("/time-analysis")
    public ResponseEntity<?> getTimeAnalysis(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问时间分布分析", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取系统统计数据
            SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

            // 获取时间分布信息
            TimeDistribution timeDistribution = statistics.timeDistribution();

            // 获取高峰时段
            List<Integer> peakHours = timeDistribution.getPeakHours();

            // 计算各小时的超时比例
            Map<Integer, Double> hourlyPercentages = new HashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                hourlyPercentages.put(hour, timeDistribution.getHourlyPercentage(hour));
            }

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("hourlyDistribution", timeDistribution.hourlyDistribution());
            responseData.put("weekdayDistribution", timeDistribution.weekdayDistribution());
            responseData.put("monthlyDistribution", timeDistribution.monthlyDistribution());
            responseData.put("peakHours", peakHours);
            responseData.put("hourlyPercentages", hourlyPercentages);
            responseData.put("highRiskHour", statistics.getHighRiskHour());
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));

            logger.info("管理员 {} 获取了时间分布分析数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取时间分布分析时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取时间分布分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取趋势预警
     * 分析超时趋势，识别显著变化并发出预警
     */
    @GetMapping("/trend-alerts")
    public ResponseEntity<?> getTrendAlerts(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0.2") double threshold,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问趋势预警", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取系统统计数据
            SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

            // 获取趋势数据
            List<TimeoutTrend> allTrends = statistics.trends();

            // 筛选显著变化的趋势
            List<TimeoutTrend> significantTrends = allTrends.stream()
                    .filter(trend -> Math.abs(trend.changeRate()) >= threshold)
                    .filter(TimeoutTrend::isSignificantChange)
                    .collect(Collectors.toList());

            // 获取增长趋势
            List<TimeoutTrend> increasingTrends = significantTrends.stream()
                    .filter(TimeoutTrend::isIncreasing)
                    .collect(Collectors.toList());

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("allTrends", allTrends);
            responseData.put("significantTrends", significantTrends);
            responseData.put("increasingTrends", increasingTrends);
            responseData.put("trendDescriptions", significantTrends.stream()
                    .collect(Collectors.toMap(
                            TimeoutTrend::timeFrame,
                            TimeoutTrend::getTrendDescription
                    )));
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));
            responseData.put("threshold", threshold);

            logger.info("管理员 {} 获取了趋势预警数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取趋势预警时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取趋势预警失败: " + e.getMessage()));
        }
    }

    /**
     * 获取服务风险分析
     * 按服务类型分析超时风险级别和建议措施
     */
    @GetMapping("/service-risk-analysis")
    public ResponseEntity<?> getServiceRiskAnalysis(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问服务风险分析", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取系统统计数据
            SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

            // 获取服务统计数据
            Map<String, ServiceTypeStatistics> serviceStats = statistics.serviceStatistics();

            // 分析每种服务的风险
            Map<String, Map<String, Object>> serviceRiskAnalysis = new HashMap<>();

            for (Map.Entry<String, ServiceTypeStatistics> entry : serviceStats.entrySet()) {
                String serviceType = entry.getKey();
                Map<String, Object> riskInfo = getStringObjectMap(entry);

                serviceRiskAnalysis.put(serviceType, riskInfo);
            }

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("serviceRiskAnalysis", serviceRiskAnalysis);
            responseData.put("isHighRiskState", statistics.isHighRiskState());
            responseData.put("highRiskPatterns", statistics.riskPatterns().stream()
                    .map(pattern -> Map.of(
                            "pattern", pattern.pattern(),
                            "occurrence", pattern.occurrence(),
                            "riskLevel", pattern.riskLevel(),
                            "description", pattern.description(),
                            "isUrgent", pattern.isUrgent(),
                            "riskLevelDescription", pattern.getRiskLevelDescription()
                    ))
                    .collect(Collectors.toList()));
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));

            logger.info("管理员 {} 获取了服务风险分析数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取服务风险分析时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取服务风险分析失败: " + e.getMessage()));
        }
    }

    @NotNull
    private static Map<String, Object> getStringObjectMap(Map.Entry<String, ServiceTypeStatistics> entry) {
        ServiceTypeStatistics stats = entry.getValue();

        // 收集服务风险信息
        Map<String, Object> riskInfo = new HashMap<>();
        riskInfo.put("timeoutRate", stats.getTimeoutRate());
        riskInfo.put("hasHighTimeoutRate", stats.hasHighTimeoutRate());
        riskInfo.put("averageTimeoutFee", stats.getAverageTimeoutFee());
        riskInfo.put("orderCount", stats.orderCount());
        riskInfo.put("timeoutCount", stats.timeoutCount());
        return riskInfo;
    }

    /**
     * 获取超时成本分析
     * 分析超时费用情况和对平台的影响
     */
    @GetMapping("/cost-analysis")
    public ResponseEntity<?> getCostAnalysis(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问超时成本分析", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            // 获取系统统计数据
            SystemTimeoutStatistics statistics = globalTimeoutStatisticsService.getSystemStatistics(period);

            // 获取服务统计数据
            Map<String, ServiceTypeStatistics> serviceStats = statistics.serviceStatistics();

            // 计算每种服务的平均超时费用
            Map<String, BigDecimal> averageTimeoutFees = new HashMap<>();
            for (Map.Entry<String, ServiceTypeStatistics> entry : serviceStats.entrySet()) {
                String serviceType = entry.getKey();
                ServiceTypeStatistics stats = entry.getValue();

                averageTimeoutFees.put(serviceType, stats.getAverageTimeoutFee());
            }

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalTimeoutFees", statistics.totalTimeoutFees());
            responseData.put("averageTimeoutFees", averageTimeoutFees);
            responseData.put("serviceBreakdown", serviceStats.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> Map.of(
                                    "timeoutCount", entry.getValue().timeoutCount(),
                                    "timeoutFees", entry.getValue().timeoutFees(),
                                    "averageFee", entry.getValue().getAverageTimeoutFee()
                            )
                    )));
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));

            logger.info("管理员 {} 获取了超时成本分析数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取超时成本分析时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取超时成本分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户风险评估
     * 当前用户的风险等级和建议
     */
    @GetMapping("/user-risk-assessment")
    public ResponseEntity<?> getUserRiskAssessment(
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 获取最近7天的统计数据
            StatisticsPeriod period = StatisticsPeriod.lastNDays(7);
            UserTimeoutStatistics statistics = globalTimeoutStatisticsService.getUserStatistics(user, period);

            // 收集风险评估信息
            boolean isHighRisk = statistics.isHighRisk();
            double overallTimeoutRate = statistics.getOverallTimeoutRate();
            int timeoutCount = statistics.getTimeoutCount();

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("userId", user.getId());
            responseData.put("username", user.getUsername());
            responseData.put("isHighRisk", isHighRisk);
            responseData.put("overallTimeoutRate", overallTimeoutRate);
            responseData.put("timeoutCount", timeoutCount);
            responseData.put("serviceStats", statistics.serviceStatistics().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> Map.of(
                                    "needsSpecialAttention", entry.getValue().needsSpecialAttention(),
                                    "latestIncident", entry.getValue().getLatestIncident(),
                                    "significantDelayCount", entry.getValue().getSignificantDelayCount(),
                                    "riskMetrics", Map.of(
                                            "riskLevel", entry.getValue().riskMetrics().getRiskLevel().toString(),
                                            "needsIntervention", entry.getValue().riskMetrics().needsIntervention(),
                                            "recommendedAction", entry.getValue().riskMetrics().getRecommendedAction()
                                    )
                            )
                    )));
            responseData.put("period", Map.of(
                    "startTime", period.startTime(),
                    "endTime", period.endTime()
            ));

            logger.info("用户 {} 获取了个人风险评估数据", user.getId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取用户风险评估时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取用户风险评估失败: " + e.getMessage()));
        }
    }

    /**
     * 获取最新超时报告
     */
    @GetMapping("/reports/latest")
    public ResponseEntity<?> getLatestReport(
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问超时报告", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            GlobalTimeoutReport report = globalTimeoutStatisticsService.getLatestReport();
            if (report == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "未找到超时报告"));
            }
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("获取最新超时报告失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取超时报告失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定时间段的超时报告
     */
    @GetMapping("/reports")
    public ResponseEntity<?> getReportsByPeriod(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查是否为管理员
        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户 {} 尝试访问超时报告", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(30);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        try {
            List<GlobalTimeoutReport> reports = globalTimeoutStatisticsService.getReportsByPeriod(startTime, endTime);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("获取超时报告失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取超时报告失败: " + e.getMessage()));
        }
    }

    /**
     * 获取超时统计建议
     * 管理员获取系统级建议，普通用户获取个人建议
     */
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        StatisticsPeriod period = createPeriod(startTime, endTime);

        try {
            List<String> recommendations;

            // 管理员获取系统级建议
            if (userService.isAdminUser(user)) {
                // 获取最新报告的建议
                GlobalTimeoutReport latestReport = globalTimeoutStatisticsService.getLatestReport();
                if (latestReport != null && latestReport.getRecommendations() != null) {
                    recommendations = latestReport.getRecommendations();
                } else {
                    // 使用服务层已有的方法生成建议，避免重复实现
                    SystemTimeoutStatistics systemStats = globalTimeoutStatisticsService.getSystemStatistics(period);
                    List<UserTimeoutRanking> userRankings = globalTimeoutStatisticsService
                            .getUserTimeoutRanking(period, 10, "timeoutRate", false);
                    recommendations = globalTimeoutStatisticsService.generateRecommendationsFromRankings(
                            systemStats, userRankings);
                }
            }
            // 普通用户获取个人建议
            else {
                recommendations = globalTimeoutStatisticsService.generateUserRecommendations(user, period);
            }

            return ResponseEntity.ok(Map.of(
                    "recommendations", recommendations,
                    "period", Map.of(
                            "startTime", period.startTime(),
                            "endTime", period.endTime()
                    )
            ));
        } catch (Exception e) {
            logger.error("生成超时统计建议失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "生成超时统计建议失败: " + e.getMessage()));
        }
    }
}