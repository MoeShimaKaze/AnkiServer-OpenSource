package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDateTime;

/**
 * 统计时间段配置
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record StatisticsPeriod(
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    public StatisticsPeriod {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time cannot be null");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }
    }

    /**
     * 创建过去N天的统计时间段
     */
    public static StatisticsPeriod lastNDays(int days) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        return new StatisticsPeriod(startTime, endTime);
    }

    /**
     * 创建今天的统计时间段
     */
    public static StatisticsPeriod today() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.toLocalDate().atStartOfDay();
        return new StatisticsPeriod(startTime, now);
    }
}