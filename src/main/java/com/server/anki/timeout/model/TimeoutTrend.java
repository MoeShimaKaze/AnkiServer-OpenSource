package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;

/**
 * 超时趋势
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record TimeoutTrend(
        String timeFrame,
        double timeoutRate,
        BigDecimal averageFee,
        double changeRate
) {
    /**
     * 判断是否为上升趋势
     */
    @JsonIgnore
    public boolean isIncreasing() {
        return changeRate > 0.05; // 变化率大于5%认为是上升趋势
    }

    /**
     * 判断是否为显著变化
     */
    @JsonIgnore
    public boolean isSignificantChange() {
        return Math.abs(changeRate) > 0.2; // 变化率超过20%认为是显著变化
    }

    /**
     * 获取趋势描述
     */
    @JsonIgnore
    public String getTrendDescription() {
        if (changeRate > 0.2) return "显著上升";
        if (changeRate > 0.05) return "轻微上升";
        if (changeRate < -0.2) return "显著下降";
        if (changeRate < -0.05) return "轻微下降";
        return "基本稳定";
    }
}