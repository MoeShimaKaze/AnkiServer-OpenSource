package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 系统超时统计
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record SystemTimeoutStatistics(
        BigDecimal totalTimeoutFees,
        Map<String, ServiceTypeStatistics> serviceStatistics,
        TimeDistribution timeDistribution,
        RegionStatistics regionStatistics,
        List<HighRiskPattern> riskPatterns,
        List<TimeoutTrend> trends
) {
    /**
     * 获取最高风险时段
     */
    @JsonIgnore
    public Integer getHighRiskHour() {  // 返回类型改为Integer，可以为null
        return timeDistribution.hourlyDistribution().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);  // 返回null而非-1
    }

    /**
     * 获取最高风险区域
     */
    @JsonIgnore
    public String getHighRiskRegion() {
        return regionStatistics.timeoutCounts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知");
    }

    /**
     * 判断系统是否处于高风险状态
     */
    @JsonIgnore
    public boolean isHighRiskState() {
        return !riskPatterns.isEmpty() &&
                riskPatterns.stream().anyMatch(pattern -> pattern.riskLevel() > 0.8);
    }
}