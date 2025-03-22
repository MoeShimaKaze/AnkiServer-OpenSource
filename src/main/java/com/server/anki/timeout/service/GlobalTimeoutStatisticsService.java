package com.server.anki.timeout.service;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.repository.PurchaseRequestRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.enums.TimeoutType;
import com.server.anki.timeout.model.*;
import com.server.anki.timeout.repository.GlobalTimeoutReportRepository;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一的超时统计服务
 * 处理所有类型订单(MailOrder、ShoppingOrder、PurchaseRequest)的超时统计
 */
@Slf4j
@Service
public class GlobalTimeoutStatisticsService {

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private GlobalTimeoutReportRepository globalTimeoutReportRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取用户的超时统计信息
     * 包含所有类型的订单(MailOrder、ShoppingOrder、PurchaseRequest)
     *
     * @param user 用户
     * @param period 统计时间段
     * @return 用户超时统计信息
     */
    @Cacheable(value = "userTimeoutStats", key = "#user.id + '_' + #period.toString()")
    public UserTimeoutStatistics getUserStatistics(User user, StatisticsPeriod period) {
        log.debug("计算用户 {} 的超时统计 - 时间区间：{} 至 {}",
                user.getId(), period.startTime(), period.endTime());

        // 获取不同类型的用户超时订单
        List<MailOrder> mailOrderTimeouts = mailOrderRepository
                .findByAssignedUserIdAndOrderStatusAndCreatedAtBetween(
                        user.getId(), OrderStatus.PLATFORM_INTERVENTION,
                        period.startTime(), period.endTime());

        List<ShoppingOrder> shoppingOrderTimeouts = shoppingOrderRepository
                .findByAssignedUserAndOrderStatusAndCreatedAtBetween(
                        user, OrderStatus.PLATFORM_INTERVENTION,
                        period.startTime(), period.endTime());

        List<PurchaseRequest> purchaseRequestTimeouts = purchaseRequestRepository
                .findByAssignedUserAndStatusAndCreatedAtBetween(
                        user, OrderStatus.PLATFORM_INTERVENTION,
                        period.startTime(), period.endTime());

        // 如果没有任何超时订单，返回空结果
        if (mailOrderTimeouts.isEmpty() && shoppingOrderTimeouts.isEmpty()
                && purchaseRequestTimeouts.isEmpty()) {
            return createEmptyUserStatistics(user.getId());
        }

        // 按服务类型分组统计
        Map<String, ServiceGroupStatistics> groupStats = new HashMap<>();

        // 处理快递订单
        if (!mailOrderTimeouts.isEmpty()) {
            ServiceTypeStatistics mailStats = calculateServiceStatistics(mailOrderTimeouts);
            List<TimeoutIncident> mailIncidents = getMailOrderTimeoutIncidents(mailOrderTimeouts);
            ServiceRiskMetrics mailRiskMetrics = calculateMailOrderRiskMetrics(mailOrderTimeouts);

            groupStats.put(TimeoutOrderType.MAIL_ORDER.name(),
                    new ServiceGroupStatistics(mailStats, mailIncidents, mailRiskMetrics));
        }

        // 处理商家订单
        if (!shoppingOrderTimeouts.isEmpty()) {
            ServiceTypeStatistics shoppingStats = calculateServiceStatistics(shoppingOrderTimeouts);
            List<TimeoutIncident> shoppingIncidents = getShoppingOrderTimeoutIncidents(shoppingOrderTimeouts);
            ServiceRiskMetrics shoppingRiskMetrics = calculateShoppingOrderRiskMetrics(shoppingOrderTimeouts);

            groupStats.put(TimeoutOrderType.SHOPPING_ORDER.name(),
                    new ServiceGroupStatistics(shoppingStats, shoppingIncidents, shoppingRiskMetrics));
        }

        // 处理代购订单
        if (!purchaseRequestTimeouts.isEmpty()) {
            ServiceTypeStatistics purchaseStats = calculateServiceStatistics(purchaseRequestTimeouts);
            List<TimeoutIncident> purchaseIncidents = getPurchaseRequestTimeoutIncidents(purchaseRequestTimeouts);
            ServiceRiskMetrics purchaseRiskMetrics = calculatePurchaseRequestRiskMetrics(purchaseRequestTimeouts);

            groupStats.put(TimeoutOrderType.PURCHASE_REQUEST.name(),
                    new ServiceGroupStatistics(purchaseStats, purchaseIncidents, purchaseRiskMetrics));
        }

        // 计算用户的总超时费用
        BigDecimal totalTimeoutFees = BigDecimal.ZERO;
        for (ServiceGroupStatistics stats : groupStats.values()) {
            totalTimeoutFees = totalTimeoutFees.add(stats.statistics().timeoutFees());
        }

        // 计算用户的总订单数
        int totalOrders = mailOrderRepository.countByAssignedUserIdAndCreatedAtBetween(
                user.getId(), period.startTime(), period.endTime());

        totalOrders += shoppingOrderRepository.countByAssignedUserAndCreatedAtBetween(
                user, period.startTime(), period.endTime());

        totalOrders += purchaseRequestRepository.countByAssignedUserAndCreatedAtBetween(
                user, period.startTime(), period.endTime());

        return new UserTimeoutStatistics(
                user.getId(),
                totalTimeoutFees,
                totalOrders,
                groupStats
        );
    }

    /**
     * 获取系统超时统计
     * 包含所有类型的订单(MailOrder、ShoppingOrder、PurchaseRequest)
     *
     * @param period 统计时间段
     * @return 系统超时统计
     */
    @Cacheable(value = "systemTimeoutStats", key = "#period.toString()")
    public SystemTimeoutStatistics getSystemStatistics(StatisticsPeriod period) {
        log.debug("计算系统超时统计 - 时间区间：{} 至 {}",
                period.startTime(), period.endTime());

        // 获取所有类型订单的超时数据
        List<Timeoutable> timeoutOrders = new ArrayList<>();
        timeoutOrders.addAll(getMailOrderTimeouts(period));
        timeoutOrders.addAll(getShoppingOrderTimeouts(period));
        timeoutOrders.addAll(getPurchaseRequestTimeouts(period));

        if (timeoutOrders.isEmpty()) {
            return createEmptySystemStatistics();
        }

        // 获取各类订单的统计数据
        Map<String, ServiceTypeStatistics> orderTypeStats = getOrderTypeStatistics(timeoutOrders);

        // 计算时间分布
        TimeDistribution timeDistribution = calculateTimeDistribution(timeoutOrders);

        // 计算区域统计
        RegionStatistics regionStats = calculateRegionStatistics(timeoutOrders);

        // 识别风险模式
        List<HighRiskPattern> riskPatterns = identifyRiskPatterns(timeoutOrders);

        // 分析趋势
        List<TimeoutTrend> trends = analyzeTrends(timeoutOrders, period);

        // 计算总超时费用
        BigDecimal totalTimeoutFees = calculateTotalTimeoutFees(timeoutOrders);

        return new SystemTimeoutStatistics(
                totalTimeoutFees,
                orderTypeStats,
                timeDistribution,
                regionStats,
                riskPatterns,
                trends
        );
    }

    /**
     * 获取用户超时排行榜
     * 包含所有类型订单的超时统计
     *
     * @param period 统计时间段
     * @param limit 返回记录数限制，0表示不限制
     * @param sortBy 排序字段
     * @param ascending 是否升序排序
     * @return 用户超时排行列表
     */
    @Cacheable(value = "userTimeoutRanking", key = "#period.toString() + '_' + #sortBy + '_' + #ascending")
    public List<UserTimeoutRanking> getUserTimeoutRanking(StatisticsPeriod period, int limit, String sortBy, boolean ascending) {
        log.info("获取用户超时排行 - 时间区间：{} 至 {}, 排序字段: {}, 升序: {}",
                period.startTime(), period.endTime(), sortBy, ascending);

        Map<Long, UserTimeoutRankingBuilder> rankingMap = new HashMap<>();

        // 获取快递代拿订单超时数据
        List<MailOrder> mailOrderTimeouts = mailOrderRepository.findByOrderStatusAndCreatedAtBetween(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());

        // 获取商家订单超时数据
        List<ShoppingOrder> shoppingOrderTimeouts = shoppingOrderRepository.findByOrderStatusAndCreatedAtBetween(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());

        // 获取代购订单超时数据
        List<PurchaseRequest> purchaseRequestTimeouts = purchaseRequestRepository.findByStatusAndCreatedAtBetween(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());

        // 处理快递代拿订单
        for (MailOrder order : mailOrderTimeouts) {
            if (order.getAssignedUser() == null) continue;

            Long userId = order.getAssignedUser().getId();
            String username = order.getAssignedUser().getUsername();

            rankingMap.computeIfAbsent(userId, id -> new UserTimeoutRankingBuilder(id, username))
                    .processMailOrder(order);
        }

        // 处理商家订单
        for (ShoppingOrder order : shoppingOrderTimeouts) {
            if (order.getAssignedUser() == null) continue;

            Long userId = order.getAssignedUser().getId();
            String username = order.getAssignedUser().getUsername();

            rankingMap.computeIfAbsent(userId, id -> new UserTimeoutRankingBuilder(id, username))
                    .processShoppingOrder(order);
        }

        // 处理代购订单
        for (PurchaseRequest order : purchaseRequestTimeouts) {
            if (order.getAssignedUser() == null) continue;

            Long userId = order.getAssignedUser().getId();
            String username = order.getAssignedUser().getUsername();

            rankingMap.computeIfAbsent(userId, id -> new UserTimeoutRankingBuilder(id, username))
                    .processPurchaseRequest(order);
        }

        // 获取各用户的总订单数
        for (UserTimeoutRankingBuilder builder : rankingMap.values()) {
            int mailOrderCount = mailOrderRepository.countByAssignedUserIdAndCreatedAtBetween(
                    builder.getUserId(), period.startTime(), period.endTime());

            int shoppingOrderCount = shoppingOrderRepository.countByAssignedUserAndCreatedAtBetween(
                    userService.getUserById(builder.getUserId()), period.startTime(), period.endTime());

            int purchaseRequestCount = purchaseRequestRepository.countByAssignedUserAndCreatedAtBetween(
                    userService.getUserById(builder.getUserId()), period.startTime(), period.endTime());

            builder.setTotalOrders(mailOrderCount + shoppingOrderCount + purchaseRequestCount);
        }

        // 构建结果并排序
        List<UserTimeoutRanking> result = rankingMap.values().stream()
                .map(UserTimeoutRankingBuilder::build)
                .collect(Collectors.toList());

        // 排序
        sortResults(result, sortBy, ascending);

        // 限制返回数量
        if (limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }

        return result;
    }

    /**
     * 获取指定用户ID的统计数据
     *
     * @param userId 用户ID
     * @param period 统计时间段
     * @return 用户超时统计
     */
    public UserTimeoutStatistics getUserStatisticsById(Long userId, StatisticsPeriod period) {
        User user = userService.getUserById(userId);
        if (user == null) {
            log.warn("未找到用户ID: {}", userId);
            return createEmptyUserStatistics(userId);
        }

        // 通过代理调用缓存方法
        GlobalTimeoutStatisticsService proxy =
                applicationContext.getBean(GlobalTimeoutStatisticsService.class);
        return proxy.getUserStatistics(user, period);
    }

    /**
     * 获取高风险用户统计
     *
     * @param period 统计时间段
     * @param limit 限制返回数量，0表示不限制
     * @return 高风险用户列表
     */
    public List<UserTimeoutStatistics> getTopTimeoutUsers(StatisticsPeriod period, int limit) {
        log.debug("获取高风险用户统计 - 时间区间：{} 至 {}", period.startTime(), period.endTime());

        // 通过代理调用缓存方法
        GlobalTimeoutStatisticsService proxy =
                applicationContext.getBean(GlobalTimeoutStatisticsService.class);

        // 获取高风险用户排名
        List<UserTimeoutRanking> rankings = proxy.getUserTimeoutRanking(period, limit > 0 ? limit : 10, "timeoutRate", false);

        // 为每个排名用户获取详细的统计信息
        return rankings.stream()
                .map(ranking -> {
                    User user = new User();
                    user.setId(ranking.getUserId());
                    return proxy.getUserStatistics(user, period);
                })
                .collect(Collectors.toList());
    }

    /**
     * 生成全局每日统计报告
     * 与generateDailyReport方法功能相同，保留此方法保持向后兼容性
     */
    @Scheduled(cron = "0 30 1 * * ?") // 设置在每日报告后半小时执行
    @Transactional
    public void generateDailyGlobalReport() {
        log.info("正在生成全局每日超时统计报告");
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(1);
        StatisticsPeriod period = new StatisticsPeriod(startTime, endTime);

        try {
            // 通过代理调用缓存方法
            GlobalTimeoutStatisticsService proxy =
                    applicationContext.getBean(GlobalTimeoutStatisticsService.class);

            SystemTimeoutStatistics systemStats = proxy.getSystemStatistics(period);

            // 获取各类型的超时用户
            List<UserTimeoutRanking> topTimeoutUsers = proxy.getUserTimeoutRanking(period, 10, "totaltimeouts", false);

            // 生成建议
            List<String> recommendations = generateRecommendationsFromRankings(systemStats, topTimeoutUsers);

            // 创建并保存全局报告
            GlobalTimeoutReport report = GlobalTimeoutReport.builder()
                    .startTime(period.startTime())
                    .endTime(period.endTime())
                    .systemStats(systemStats)
                    .topTimeoutUsers(topTimeoutUsers.stream()
                            .map(ranking -> {
                                User user = new User();
                                user.setId(ranking.getUserId());
                                return proxy.getUserStatistics(user, period);
                            })
                            .collect(Collectors.toList()))
                    .recommendations(recommendations)
                    .build();

            globalTimeoutReportRepository.save(report);

            // 清理旧报告
            cleanupGlobalOldReports();

            log.info("全局每日统计报告生成成功");
        } catch (Exception e) {
            log.error("生成全局每日统计报告失败: {}", e.getMessage(), e);
            throw new StatisticsGenerationException("生成全局每日报告失败", e);
        }
    }

    // 以下是辅助方法

    /**
     * 获取快递代拿订单超时数据
     */
    private List<MailOrder> getMailOrderTimeouts(StatisticsPeriod period) {
        return mailOrderRepository.findByOrderStatusAndCreatedAtBetweenOrderByAssignedUserIdAsc(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());
    }

    /**
     * 获取商家订单超时数据
     */
    private List<ShoppingOrder> getShoppingOrderTimeouts(StatisticsPeriod period) {
        return shoppingOrderRepository.findByOrderStatusAndCreatedAtBetween(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());
    }

    /**
     * 获取代购订单超时数据
     */
    private List<PurchaseRequest> getPurchaseRequestTimeouts(StatisticsPeriod period) {
        return purchaseRequestRepository.findByStatusAndCreatedAtBetween(
                OrderStatus.PLATFORM_INTERVENTION, period.startTime(), period.endTime());
    }

    /**
     * 按订单类型计算统计数据
     */
    private Map<String, ServiceTypeStatistics> getOrderTypeStatistics(List<Timeoutable> orders) {
        // 按订单类型分组
        Map<TimeoutOrderType, List<Timeoutable>> ordersByType = orders.stream()
                .collect(Collectors.groupingBy(Timeoutable::getTimeoutOrderType));

        Map<String, ServiceTypeStatistics> result = new HashMap<>();

        // 计算每种订单类型的统计数据
        for (Map.Entry<TimeoutOrderType, List<Timeoutable>> entry : ordersByType.entrySet()) {
            TimeoutOrderType orderType = entry.getKey();
            List<Timeoutable> typeOrders = entry.getValue();

            ServiceTypeStatistics stats = calculateServiceStatistics(typeOrders);
            result.put(orderType.name(), stats);
        }

        return result;
    }

    /**
     * 计算服务类型统计数据
     * 使用泛型参数以避免类型转换警告
     */
    @SuppressWarnings("unchecked")
    private <T> ServiceTypeStatistics calculateServiceStatistics(List<T> orders) {
        int totalOrders = orders.size();
        BigDecimal timeoutFees = BigDecimal.ZERO;
        double totalDelay = 0;

        if (orders.isEmpty()) {
            return new ServiceTypeStatistics(0, 0, BigDecimal.ZERO, 0.0);
        }

        Object firstItem = orders.get(0);

        if (firstItem instanceof MailOrder) {
            timeoutFees = calculateMailOrderTimeoutFees((List<MailOrder>) orders);
            totalDelay = calculateMailOrderTotalDelay((List<MailOrder>) orders);
        } else if (firstItem instanceof ShoppingOrder) {
            timeoutFees = calculateShoppingOrderTimeoutFees((List<ShoppingOrder>) orders);
            totalDelay = calculateShoppingOrderTotalDelay((List<ShoppingOrder>) orders);
        } else if (firstItem instanceof PurchaseRequest) {
            timeoutFees = calculatePurchaseRequestTimeoutFees((List<PurchaseRequest>) orders);
            totalDelay = calculatePurchaseRequestTotalDelay((List<PurchaseRequest>) orders);
        } else if (firstItem instanceof Timeoutable) {
            timeoutFees = calculateTimeoutableTimeoutFees((List<Timeoutable>) orders);
            totalDelay = calculateTimeoutableTotalDelay((List<Timeoutable>) orders);
        }

        double averageDelay = totalDelay / totalOrders;

        return new ServiceTypeStatistics(
                totalOrders,
                totalOrders, // 对于已过滤的超时订单列表，总数等于超时数
                timeoutFees,
                averageDelay
        );
    }

    /**
     * 计算MailOrder类型订单的超时费用
     */
    private BigDecimal calculateMailOrderTimeoutFees(List<MailOrder> orders) {
        return orders.stream()
                .map(order -> BigDecimal.valueOf(order.getPlatformIncome()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算ShoppingOrder类型订单的超时费用
     */
    private BigDecimal calculateShoppingOrderTimeoutFees(List<ShoppingOrder> orders) {
        return orders.stream()
                .map(ShoppingOrder::getPlatformFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算PurchaseRequest类型订单的超时费用
     */
    private BigDecimal calculatePurchaseRequestTimeoutFees(List<PurchaseRequest> orders) {
        return orders.stream()
                .map(order -> BigDecimal.valueOf(order.getPlatformIncome()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算Timeoutable类型订单的超时费用
     */
    private BigDecimal calculateTimeoutableTimeoutFees(List<Timeoutable> orders) {
        return orders.stream()
                .map(order -> switch (order.getTimeoutOrderType()) {
                    case MAIL_ORDER -> {
                        MailOrder mailOrder = (MailOrder) order;
                        yield BigDecimal.valueOf(mailOrder.getPlatformIncome());
                    }
                    case SHOPPING_ORDER -> {
                        ShoppingOrder shoppingOrder = (ShoppingOrder) order;
                        yield shoppingOrder.getPlatformFee();
                    }
                    case PURCHASE_REQUEST -> {
                        PurchaseRequest purchaseRequest = (PurchaseRequest) order;
                        yield BigDecimal.valueOf(purchaseRequest.getPlatformIncome());
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算MailOrder类型订单的总延迟时间
     */
    private double calculateMailOrderTotalDelay(List<MailOrder> orders) {
        return orders.stream()
                .mapToLong(this::calculateDelay)
                .sum();
    }

    /**
     * 计算ShoppingOrder类型订单的总延迟时间
     */
    private double calculateShoppingOrderTotalDelay(List<ShoppingOrder> orders) {
        return orders.stream()
                .mapToLong(this::calculateDelay)
                .sum();
    }

    /**
     * 计算PurchaseRequest类型订单的总延迟时间
     */
    private double calculatePurchaseRequestTotalDelay(List<PurchaseRequest> orders) {
        return orders.stream()
                .mapToLong(this::calculateDelay)
                .sum();
    }

    /**
     * 计算Timeoutable类型订单的总延迟时间
     */
    private double calculateTimeoutableTotalDelay(List<Timeoutable> orders) {
        return orders.stream()
                .mapToLong(this::calculateDelay)
                .sum();
    }

    /**
     * 获取超时事件记录（快递订单）
     */
    private List<TimeoutIncident> getMailOrderTimeoutIncidents(List<MailOrder> orders) {
        return orders.stream()
                .map(order -> new TimeoutIncident(
                        order.getOrderNumber(),
                        order.getInterventionTime(),
                        TimeoutType.DELIVERY.name(),
                        BigDecimal.valueOf(order.getPlatformIncome()),
                        order.getDeliveryService(),
                        calculateDelay(order)
                ))
                .sorted(Comparator.comparing(TimeoutIncident::timestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取超时事件记录（商家订单）
     */
    private List<TimeoutIncident> getShoppingOrderTimeoutIncidents(List<ShoppingOrder> orders) {
        return orders.stream()
                .map(order -> new TimeoutIncident(
                        order.getOrderNumber(),
                        order.getInterventionTime(),
                        TimeoutType.DELIVERY.name(),
                        order.getPlatformFee(),
                        null,
                        calculateDelay(order)
                ))
                .sorted(Comparator.comparing(TimeoutIncident::timestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取超时事件记录（代购订单）
     */
    private List<TimeoutIncident> getPurchaseRequestTimeoutIncidents(List<PurchaseRequest> orders) {
        return orders.stream()
                .map(order -> new TimeoutIncident(
                        order.getRequestNumber(),
                        order.getInterventionTime(),
                        TimeoutType.DELIVERY.name(),
                        BigDecimal.valueOf(order.getPlatformIncome()),
                        null,
                        calculateDelay(order)
                ))
                .sorted(Comparator.comparing(TimeoutIncident::timestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 计算订单延迟时间（分钟）
     */
    private long calculateDelay(Timeoutable order) {
        if (order.getInterventionTime() != null && order.getExpectedDeliveryTime() != null) {
            return ChronoUnit.MINUTES.between(
                    order.getExpectedDeliveryTime(),
                    order.getInterventionTime()
            );
        }
        return 0L;
    }

    /**
     * 计算风险指标（快递订单）
     */
    private ServiceRiskMetrics calculateMailOrderRiskMetrics(List<MailOrder> orders) {
        if (orders.isEmpty()) {
            return new ServiceRiskMetrics(0.0, 0.0, 0.0);
        }

        double avgDelay = orders.stream()
                .mapToLong(this::calculateDelay)
                .average()
                .orElse(0.0);

        double severityScore = calculateSeverityScore(avgDelay);
        return new ServiceRiskMetrics(100.0, avgDelay, severityScore);
    }

    /**
     * 计算风险指标（商家订单）
     */
    private ServiceRiskMetrics calculateShoppingOrderRiskMetrics(List<ShoppingOrder> orders) {
        if (orders.isEmpty()) {
            return new ServiceRiskMetrics(0.0, 0.0, 0.0);
        }

        double avgDelay = orders.stream()
                .mapToLong(this::calculateDelay)
                .average()
                .orElse(0.0);

        double severityScore = calculateSeverityScore(avgDelay);
        return new ServiceRiskMetrics(100.0, avgDelay, severityScore);
    }

    /**
     * 计算风险指标（代购订单）
     */
    private ServiceRiskMetrics calculatePurchaseRequestRiskMetrics(List<PurchaseRequest> orders) {
        if (orders.isEmpty()) {
            return new ServiceRiskMetrics(0.0, 0.0, 0.0);
        }

        double avgDelay = orders.stream()
                .mapToLong(this::calculateDelay)
                .average()
                .orElse(0.0);

        double severityScore = calculateSeverityScore(avgDelay);
        return new ServiceRiskMetrics(100.0, avgDelay, severityScore);
    }

    /**
     * 计算严重程度分数
     */
    private double calculateSeverityScore(double avgDelay) {
        double normalizedRate = 1.0;
        double normalizedDelay = Math.min(avgDelay / (60 * 24), 1.0);
        return (normalizedRate * 0.7 + normalizedDelay * 0.3) * 100;
    }

    /**
     * 分析超时趋势
     */
    private List<TimeoutTrend> analyzeTrends(List<Timeoutable> orders, StatisticsPeriod period) {
        List<TimeoutTrend> trends = new ArrayList<>();

        // 定义时间段分析配置
        record TimeFrameConfig(String name, ChronoUnit unit) {}
        List<TimeFrameConfig> timeFrameConfigs = Arrays.asList(
                new TimeFrameConfig("日", ChronoUnit.DAYS),
                new TimeFrameConfig("周", ChronoUnit.WEEKS),
                new TimeFrameConfig("月", ChronoUnit.MONTHS)
        );

        for (TimeFrameConfig config : timeFrameConfigs) {
            TimeoutTrend trend = analyzeTrendForTimeFrame(orders, period, config.name(), config.unit());
            if (trend != null) {
                trends.add(trend);
            }
        }

        return trends;
    }

    /**
     * 分析特定时间段的趋势
     */
    private TimeoutTrend analyzeTrendForTimeFrame(List<Timeoutable> orders, StatisticsPeriod period,
                                                  String timeFrame, ChronoUnit unit) {
        // 获取当前时间段的订单
        List<Timeoutable> currentPeriodOrders = orders.stream()
                .filter(order -> order.getCreatedTime().isAfter(period.startTime()))
                .collect(Collectors.toList());

        if (currentPeriodOrders.isEmpty()) {
            return null;
        }

        // 计算当前时间段的统计数据
        BigDecimal totalFees = calculateTotalTimeoutFees(currentPeriodOrders);
        double avgFee = totalFees.divide(BigDecimal.valueOf(Math.max(1, currentPeriodOrders.size())), 2, RoundingMode.HALF_UP)
                .doubleValue();

        // 获取上一时间段的订单
        List<Timeoutable> previousPeriodOrders = orders.stream()
                .filter(order -> order.getCreatedTime().isBefore(period.startTime()) &&
                        order.getCreatedTime().isAfter(period.startTime().minus(1, unit)))
                .toList();

        // 计算变化率
        double previousRate = previousPeriodOrders.isEmpty() ? 0.0 :
                (double) previousPeriodOrders.size() / Math.max(1, orders.size()) * 100;
        double currentRate = (double) currentPeriodOrders.size() / Math.max(1, orders.size()) * 100;
        double changeRate = previousRate == 0 ? 0 : (currentRate - previousRate) / previousRate;

        return new TimeoutTrend(
                timeFrame,
                currentRate,
                BigDecimal.valueOf(avgFee),
                changeRate
        );
    }

    /**
     * 计算时间分布
     */
    private TimeDistribution calculateTimeDistribution(List<Timeoutable> orders) {
        Map<Integer, Integer> hourlyDistribution = new HashMap<>();
        Map<String, Integer> weekdayDistribution = new HashMap<>();
        Map<Integer, Integer> monthlyDistribution = new HashMap<>();

        for (Timeoutable order : orders) {
            LocalDateTime timeoutTime = order.getInterventionTime();
            if (timeoutTime != null) {
                hourlyDistribution.merge(timeoutTime.getHour(), 1, Integer::sum);
                weekdayDistribution.merge(timeoutTime.getDayOfWeek().toString(), 1, Integer::sum);
                monthlyDistribution.merge(timeoutTime.getDayOfMonth(), 1, Integer::sum);
            }
        }

        return new TimeDistribution(
                hourlyDistribution,
                weekdayDistribution,
                monthlyDistribution
        );
    }

    /**
     * 计算区域统计信息
     */
    private RegionStatistics calculateRegionStatistics(List<Timeoutable> orders) {
        Map<String, Integer> timeoutCounts = new HashMap<>();
        Map<String, Double> timeoutRates = new HashMap<>();
        List<String> highRiskRegions = new ArrayList<>();

        // 按区域分组统计超时数量
        orders.forEach(order -> {
            String region = extractRegion(getDeliveryAddress(order));
            timeoutCounts.merge(region, 1, Integer::sum);
        });

        // 计算各区域超时率
        timeoutCounts.forEach((region, timeoutCount) -> {
            // 获取该区域的总订单数
            long areaOrderCount = orders.stream()
                    .filter(o -> extractRegion(getDeliveryAddress(o)).equals(region))
                    .count();

            double rate = areaOrderCount == 0 ? 0 : (timeoutCount / (double)areaOrderCount) * 100;
            timeoutRates.put(region, rate);

            if (rate > 15.0) { // 超时率大于15%认为是高风险区域
                highRiskRegions.add(region);
            }
        });

        return new RegionStatistics(timeoutCounts, timeoutRates, highRiskRegions);
    }

    /**
     * 提取订单的配送地址
     */
    private String getDeliveryAddress(Timeoutable order) {
        if (order == null) {
            return "未知地址";
        }

        return switch (order.getTimeoutOrderType()) {
            case MAIL_ORDER -> ((MailOrder) order).getDeliveryAddress();
            case SHOPPING_ORDER -> ((ShoppingOrder) order).getDeliveryAddress();
            case PURCHASE_REQUEST -> ((PurchaseRequest) order).getDeliveryAddress();
        };
    }

    /**
     * 提取地址中的区域信息
     */
    private String extractRegion(String address) {
        if (address == null || address.isEmpty()) {
            return "未知区域";
        }
        String[] parts = address.split("[市区县]");
        return parts.length > 0 ? parts[0] + "区" : "未知区域";
    }

    /**
     * 识别风险模式
     */
    private List<HighRiskPattern> identifyRiskPatterns(List<Timeoutable> orders) {
        List<HighRiskPattern> patterns = new ArrayList<>();

        // 检查区域性超时模式
        Map<String, List<Timeoutable>> regionOrders = orders.stream()
                .collect(Collectors.groupingBy(order -> extractRegion(getDeliveryAddress(order))));

        // 计算区域风险模式
        for (Map.Entry<String, List<Timeoutable>> entry : regionOrders.entrySet()) {
            String region = entry.getKey();
            List<Timeoutable> regionOrderList = entry.getValue();

            double timeoutRate = (double) regionOrderList.size() / Math.max(1, orders.size()) * 100;
            if (timeoutRate > 15.0) {
                patterns.add(new HighRiskPattern(
                        "区域性超时:" + region,
                        regionOrderList.size(),
                        timeoutRate / 100,
                        String.format("%s地区出现%d次超时，超时率%.1f%%",
                                region, regionOrderList.size(), timeoutRate)
                ));
            }
        }

        // 检查订单类型超时模式
        Map<TimeoutOrderType, List<Timeoutable>> typeOrders = orders.stream()
                .collect(Collectors.groupingBy(Timeoutable::getTimeoutOrderType));

        for (Map.Entry<TimeoutOrderType, List<Timeoutable>> entry : typeOrders.entrySet()) {
            TimeoutOrderType orderType = entry.getKey();
            List<Timeoutable> typeOrderList = entry.getValue();

            // 统计连续超时
            int maxConsecutiveTimeouts = findMaxConsecutiveTimeouts(typeOrderList);
            if (maxConsecutiveTimeouts >= 3) {
                patterns.add(new HighRiskPattern(
                        "连续超时:" + orderType.getDescription(),
                        maxConsecutiveTimeouts,
                        maxConsecutiveTimeouts / 10.0,
                        String.format("%s出现%d次连续超时", orderType.getDescription(), maxConsecutiveTimeouts)
                ));
            }
        }

        return patterns;
    }

    /**
     * 查找最大连续超时次数
     */
    private int findMaxConsecutiveTimeouts(List<Timeoutable> orders) {
        int currentCount = 0;
        int maxCount = 0;

        for (Timeoutable order : orders) {
            if (order.getOrderStatus() == OrderStatus.PLATFORM_INTERVENTION) {
                currentCount++;
                maxCount = Math.max(maxCount, currentCount);
            } else {
                currentCount = 0;
            }
        }

        return maxCount;
    }

    /**
     * 计算总超时费用
     */
    private BigDecimal calculateTotalTimeoutFees(List<Timeoutable> orders) {
        return orders.stream()
                .map(order -> switch (order.getTimeoutOrderType()) {
                    case MAIL_ORDER -> {
                        MailOrder mailOrder = (MailOrder) order;
                        yield BigDecimal.valueOf(mailOrder.getPlatformIncome());
                    }
                    case SHOPPING_ORDER -> {
                        ShoppingOrder shoppingOrder = (ShoppingOrder) order;
                        yield shoppingOrder.getPlatformFee();
                    }
                    case PURCHASE_REQUEST -> {
                        PurchaseRequest purchaseRequest = (PurchaseRequest) order;
                        yield BigDecimal.valueOf(purchaseRequest.getPlatformIncome());
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 创建空的用户统计信息
     */
    private UserTimeoutStatistics createEmptyUserStatistics(Long userId) {
        return new UserTimeoutStatistics(
                userId,
                BigDecimal.ZERO,
                0,
                new HashMap<>()
        );
    }

    /**
     * 创建空的系统统计信息
     */
    private SystemTimeoutStatistics createEmptySystemStatistics() {
        return new SystemTimeoutStatistics(
                BigDecimal.ZERO,
                new HashMap<>(),
                new TimeDistribution(new HashMap<>(), new HashMap<>(), new HashMap<>()),
                new RegionStatistics(new HashMap<>(), new HashMap<>(), List.of()),
                List.of(),
                List.of()
        );
    }


    /**
     * 从UserTimeoutRanking生成建议
     * 修复：添加对getHighRiskHour()返回值的null检查，避免空指针异常
     */
    public List<String> generateRecommendationsFromRankings(SystemTimeoutStatistics systemStats,
                                                            List<UserTimeoutRanking> topTimeoutUsers) {
        List<String> recommendations = new ArrayList<>();

        // 添加基于风险模式的建议
        systemStats.riskPatterns().stream()
                .filter(pattern -> pattern.occurrence() >= 3)
                .forEach(pattern -> recommendations.add(
                        String.format("【%s】需要关注：%s",
                                getRiskLevelDescription(pattern.riskLevel()),
                                pattern.description())
                ));

        // 添加基于趋势的建议
        systemStats.trends().stream()
                .filter(trend -> Math.abs(trend.changeRate()) > 0.2)
                .forEach(trend -> recommendations.add(
                        String.format("【%s趋势】%s时间段的超时率%s%.1f%%，平均超时费用%.2f元",
                                trend.timeFrame(),
                                getChangeDescription(trend.changeRate()),
                                trend.changeRate() > 0 ? "上升" : "下降",
                                Math.abs(trend.changeRate() * 100),
                                trend.averageFee().doubleValue())
                ));

        // 添加基于高风险用户的建议
        long highRiskUsersCount = topTimeoutUsers.stream()
                .filter(ranking -> ranking.getTimeoutRate() > 15.0) // 超时率大于15%视为高风险
                .count();

        if (highRiskUsersCount > 0) {
            recommendations.add(String.format(
                    "【用户风险】当前存在%d个高风险用户，建议进行针对性跟进", highRiskUsersCount));
        }

        // 添加区域建议
        if (!systemStats.regionStatistics().highRiskRegions().isEmpty()) {
            String regions = String.join("、", systemStats.regionStatistics().highRiskRegions());
            recommendations.add(String.format(
                    "【区域风险】%s地区超时率较高，建议增加配送资源或优化配送路线", regions));
        }

        // 添加高峰时段建议 - 修复空指针异常
        Integer highRiskHour = systemStats.getHighRiskHour();
        if (highRiskHour != null && highRiskHour >= 0) {
            recommendations.add(String.format(
                    "【时段风险】%d:00-%d:00时段超时率较高，建议增加该时段的配送员调度",
                    highRiskHour, highRiskHour + 1));
        } else {
            // 提供替代建议，避免缺少此类型的建议
            recommendations.add("【时段分析】暂未检测到明显的高风险时段，建议持续监控各时段超时率变化。");
        }

        return recommendations;
    }

    /**
     * 获取风险等级描述
     */
    private String getRiskLevelDescription(double riskLevel) {
        if (riskLevel > 0.8) return "极高风险";
        if (riskLevel > 0.6) return "高风险";
        if (riskLevel > 0.4) return "中等风险";
        return "低风险";
    }

    /**
     * 获取变化趋势描述
     */
    private String getChangeDescription(double changeRate) {
        if (changeRate > 0.5) return "显著上升";
        if (changeRate > 0.2) return "上升";
        if (changeRate < -0.5) return "显著下降";
        if (changeRate < -0.2) return "下降";
        return "基本稳定";
    }

    /**
     * 清理全局旧报告
     */
    @Transactional
    protected void cleanupGlobalOldReports() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30); // 保留30天的报告
        globalTimeoutReportRepository.deleteByGeneratedTimeBefore(threshold);
    }

    /**
     * 根据指定字段对结果进行排序
     */
    private void sortResults(List<UserTimeoutRanking> rankings, String sortBy, boolean ascending) {
        Comparator<UserTimeoutRanking> comparator = switch (sortBy.toLowerCase()) {
            case "totaltimeouts" -> Comparator.comparing(UserTimeoutRanking::getTotalTimeouts);
            case "pickuptimeouts" -> Comparator.comparing(UserTimeoutRanking::getPickupTimeouts);
            case "deliverytimeouts" -> Comparator.comparing(UserTimeoutRanking::getDeliveryTimeouts);
            case "confirmationtimeouts" -> Comparator.comparing(UserTimeoutRanking::getConfirmationTimeouts);
            case "totalorders" -> Comparator.comparing(UserTimeoutRanking::getTotalOrders);
            default -> Comparator.comparing(UserTimeoutRanking::getTimeoutRate);
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        rankings.sort(comparator);
    }

    /**
     * 用户超时排行构建器内部类
     */
    private static class UserTimeoutRankingBuilder {
        @Getter
        private final Long userId;
        private final String username;
        private int pickupTimeouts;
        private int deliveryTimeouts;
        private int confirmationTimeouts;
        @Setter
        private int totalOrders;

        public UserTimeoutRankingBuilder(Long userId, String username) {
            this.userId = userId;
            this.username = username;
            this.pickupTimeouts = 0;
            this.deliveryTimeouts = 0;
            this.confirmationTimeouts = 0;
            this.totalOrders = 0;
        }

        public void processMailOrder(MailOrder order) {
            processTimeoutStatus(order.getTimeoutStatus(), order.getOrderStatus());
        }

        public void processShoppingOrder(ShoppingOrder order) {
            processTimeoutStatus(order.getTimeoutStatus(), order.getOrderStatus());
        }

        public void processPurchaseRequest(PurchaseRequest request) {
            processTimeoutStatus(request.getTimeoutStatus(), request.getStatus());
        }

        private void processTimeoutStatus(TimeoutStatus timeoutStatus, OrderStatus orderStatus) {
            if (timeoutStatus != null) {
                switch (timeoutStatus) {
                    case PICKUP_TIMEOUT:
                        pickupTimeouts++;
                        break;
                    case DELIVERY_TIMEOUT:
                        deliveryTimeouts++;
                        break;
                    case CONFIRMATION_TIMEOUT:
                        confirmationTimeouts++;
                        break;
                    default:
                        if (orderStatus == OrderStatus.PLATFORM_INTERVENTION) {
                            deliveryTimeouts++;
                        }
                        break;
                }
            } else if (orderStatus == OrderStatus.PLATFORM_INTERVENTION) {
                deliveryTimeouts++;
            }
        }

        public UserTimeoutRanking build() {
            UserTimeoutRanking ranking = new UserTimeoutRanking();
            ranking.setUserId(userId);
            ranking.setUsername(username);
            ranking.setPickupTimeouts(pickupTimeouts);
            ranking.setDeliveryTimeouts(deliveryTimeouts);
            ranking.setConfirmationTimeouts(confirmationTimeouts);
            ranking.setTotalTimeouts(pickupTimeouts + deliveryTimeouts + confirmationTimeouts);
            ranking.setTotalOrders(totalOrders);

            if (totalOrders > 0) {
                double rate = (double) ranking.getTotalTimeouts() / totalOrders * 100;
                ranking.setTimeoutRate(Math.round(rate * 100) / 100.0);
            } else {
                ranking.setTimeoutRate(0.0);
            }

            return ranking;
        }
    }

    /**
     * 获取最新的全局超时报告
     * 修复：当没有报告时返回默认报告而不是null
     * @return 最新的超时报告（永远不会返回null）
     */
    public GlobalTimeoutReport getLatestReport() {
        List<GlobalTimeoutReport> reports = globalTimeoutReportRepository.findTop30ByOrderByGeneratedTimeDesc();
        if (reports != null && !reports.isEmpty()) {
            return reports.get(0);
        }

        // 如果没有报告，创建一个默认报告
        log.info("未找到历史超时报告记录，将生成默认报告");
        return createDefaultReport();
    }

    /**
     * 创建默认的超时报告
     */
    private GlobalTimeoutReport createDefaultReport() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(7);

        // 使用默认构造方法创建实例
        GlobalTimeoutReport defaultReport = new GlobalTimeoutReport();

        // 使用setter方法设置属性
        defaultReport.setGeneratedTime(now);
        defaultReport.setStartTime(startTime);
        defaultReport.setEndTime(now);
        defaultReport.setSystemStats(createEmptySystemStatistics());
        defaultReport.setTopTimeoutUsers(new ArrayList<>());

        // 添加默认建议
        List<String> defaultRecommendations = new ArrayList<>();
        defaultRecommendations.add("【系统提示】目前暂无历史超时数据，这是一份默认报告。");
        defaultRecommendations.add("【配送建议】请保持良好的配送习惯，遵循时效要求。");
        defaultRecommendations.add("【时间规划】合理安排配送路线，避免在高峰时段接单过多。");
        defaultRecommendations.add("【预防措施】关注天气和交通状况，及时调整配送计划。");
        defaultRecommendations.add("【服务质量】保持良好的服务态度，提高客户满意度。");
        defaultReport.setRecommendations(defaultRecommendations);

        return defaultReport;
    }

    /**
     * 获取指定时间段的超时报告
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的报告列表
     */
    public List<GlobalTimeoutReport> getReportsByPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        return globalTimeoutReportRepository.findByGeneratedTimeBetween(startTime, endTime);
    }

    /**
     * 生成用户特定的配送建议
     * @param user 用户
     * @param period 统计周期
     * @return 建议列表
     */
    public List<String> generateUserRecommendations(User user, StatisticsPeriod period) {
        List<String> recommendations = new ArrayList<>();

        try {
            // 获取用户统计数据
            @SuppressWarnings("SpringCacheableMethodCallsInspection") UserTimeoutStatistics userStats = getUserStatistics(user, period);

            // 根据超时率生成建议
            double overallTimeoutRate = userStats.getOverallTimeoutRate();
            if (overallTimeoutRate > 20.0) {
                recommendations.add("【严重警告】您的超时率超过20%，这可能影响您的接单权限，请立即改善配送效率。");
            } else if (overallTimeoutRate > 10.0) {
                recommendations.add("【警告】您的超时率超过10%，建议提高配送效率，避免影响后续接单。");
            } else if (overallTimeoutRate > 5.0) {
                recommendations.add("【提醒】您的超时率略高，建议合理规划路线，提高配送效率。");
            } else {
                recommendations.add("【表扬】您的超时率处于良好水平，请继续保持！");
            }

            // 根据订单类型分析提供建议
            if (userStats.serviceStatistics() != null) {
                for (Map.Entry<String, ServiceGroupStatistics> entry : userStats.serviceStatistics().entrySet()) {
                    String serviceType = entry.getKey();
                    ServiceGroupStatistics stats = entry.getValue();

                    if (stats.statistics().hasHighTimeoutRate()) {
                        recommendations.add(String.format("【%s】您在%s服务的超时率较高，建议优先改善该类型订单的配送。", serviceType, serviceType));
                    }

                    // 添加基于风险级别的建议
                    if (stats.riskMetrics() != null) {
                        RiskLevel riskLevel = stats.riskMetrics().getRiskLevel();
                        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
                            recommendations.add(String.format("【高风险】%s服务存在高风险，建议: %s",
                                    serviceType, stats.riskMetrics().getRecommendedAction()));
                        }
                    }
                }
            }

            // 添加通用建议
            recommendations.add("【时间管理】合理安排取件和配送时间，留出应对意外情况的缓冲时间。");
            recommendations.add("【路线规划】使用导航工具规划最优配送路线，减少不必要的绕路。");
            recommendations.add("【沟通技巧】遇到可能延误的情况，请提前与客户沟通，减少投诉和差评。");

        } catch (Exception e) {
            log.error("生成用户建议时发生错误: {}", e.getMessage(), e);
            recommendations.add("【系统提示】暂时无法生成详细的个性化建议，请稍后再试。");
        }

        return recommendations;
    }
}