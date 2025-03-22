package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * 时间分布统计
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record TimeDistribution(
        Map<Integer, Integer> hourlyDistribution,
        Map<String, Integer> weekdayDistribution,
        Map<Integer, Integer> monthlyDistribution
) {
    /**
     * 获取高峰时段
     */
    @JsonIgnore
    public List<Integer> getPeakHours() {
        int maxCount = hourlyDistribution.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        return hourlyDistribution.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 获取每小时的超时比例
     */
    @JsonIgnore
    public double getHourlyPercentage(int hour) {
        int total = hourlyDistribution.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return total == 0 ? 0.0 :
                (double) hourlyDistribution.getOrDefault(hour, 0) / total * 100;
    }

    /**
     * 判断是否为工作日高峰时段
     */
    @JsonIgnore
    public boolean isWeekdayPeak(String weekday, int hour) {
        return weekdayDistribution.getOrDefault(weekday, 0) >
                (weekdayDistribution.values().stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0) * 1.2) &&
                (hour >= 9 && hour <= 18);
    }
}