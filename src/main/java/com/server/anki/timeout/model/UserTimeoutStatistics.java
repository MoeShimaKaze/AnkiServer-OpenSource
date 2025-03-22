package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户超时统计信息
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record UserTimeoutStatistics(
        Long userId,
        BigDecimal totalTimeoutFees,
        int totalOrders,
        Map<String, ServiceGroupStatistics> serviceStatistics
) {
    /**
     * 计算总体超时率
     */
    @JsonIgnore
    public double getOverallTimeoutRate() {
        if (totalOrders == 0) return 0.0;
        int totalTimeouts = serviceStatistics.values().stream()
                .mapToInt(stats -> stats.statistics().timeoutCount())
                .sum();
        return (double) totalTimeouts / totalOrders * 100;
    }

    /**
     * 获取指定服务类型的统计数据
     */
    @JsonIgnore
    public ServiceTypeStatistics getServiceStatistics(String service) {
        return serviceStatistics.getOrDefault(service,
                new ServiceGroupStatistics(
                        new ServiceTypeStatistics(0, 0, BigDecimal.ZERO, 0.0),
                        List.of(),
                        new ServiceRiskMetrics(0.0, 0.0, 0.0)
                )).statistics();
    }

    /**
     * 判断是否为高风险用户
     */
    @JsonIgnore
    public boolean isHighRisk() {
        return getOverallTimeoutRate() > 20.0 || // 超时率超过20%
                totalTimeoutFees.compareTo(BigDecimal.valueOf(100)) > 0; // 超时费用超过100
    }

    /**
     * 获取超时次数
     */
    @JsonIgnore
    public int getTimeoutCount() {
        return serviceStatistics.values().stream()
                .mapToInt(stats -> stats.statistics().timeoutCount())
                .sum();
    }
}