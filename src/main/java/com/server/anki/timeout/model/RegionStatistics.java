package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * 区域统计信息
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record RegionStatistics(
        Map<String, Integer> timeoutCounts,
        Map<String, Double> timeoutRates,
        List<String> highRiskRegions
) {
    /**
     * 获取区域超时率
     */
    @JsonIgnore
    public double getRegionTimeoutRate(String region) {
        return timeoutRates.getOrDefault(region, 0.0);
    }

    /**
     * 判断是否为高风险区域
     */
    @JsonIgnore
    public boolean isHighRiskRegion(String region) {
        return highRiskRegions.contains(region);
    }

    /**
     * 获取超时次数最多的区域
     */
    @JsonIgnore
    public String getMostTimeoutRegion() {
        return timeoutCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知区域");
    }
}