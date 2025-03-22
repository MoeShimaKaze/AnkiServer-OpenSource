package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 服务类型统计
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record ServiceTypeStatistics(
        int orderCount,
        int timeoutCount,
        BigDecimal timeoutFees,
        double averageDelay
) {
    private static final int SCALE = 2;
    private static final double HIGH_TIMEOUT_RATE_THRESHOLD = 15.0;

    /**
     * 计算超时率（百分比）
     */
    @JsonIgnore
    public double getTimeoutRate() {
        return orderCount == 0 ? 0.0 : (double) timeoutCount / orderCount * 100;
    }

    /**
     * 判断是否为高超时率
     */
    @JsonIgnore
    public boolean hasHighTimeoutRate() {
        return getTimeoutRate() > HIGH_TIMEOUT_RATE_THRESHOLD;
    }

    /**
     * 计算平均超时费用
     */
    @JsonIgnore
    public BigDecimal getAverageTimeoutFee() {
        return timeoutCount == 0 ? BigDecimal.ZERO :
                timeoutFees.divide(BigDecimal.valueOf(timeoutCount), SCALE, RoundingMode.HALF_UP);
    }
}