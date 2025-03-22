package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * 服务分组统计信息
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record ServiceGroupStatistics(
        ServiceTypeStatistics statistics,
        List<TimeoutIncident> incidents,
        ServiceRiskMetrics riskMetrics
) {
    /**
     * 获取最近的超时事件
     */
    @JsonIgnore
    public TimeoutIncident getLatestIncident() {
        return incidents.stream()
                .min((i1, i2) -> i2.timestamp().compareTo(i1.timestamp()))
                .orElse(null);
    }

    /**
     * 获取严重超时事件数量
     */
    @JsonIgnore
    public long getSignificantDelayCount() {
        return incidents.stream()
                .filter(TimeoutIncident::isSignificantDelay)
                .count();
    }

    /**
     * 判断是否需要特别关注
     */
    @JsonIgnore
    public boolean needsSpecialAttention() {
        return statistics.hasHighTimeoutRate() ||
                riskMetrics.needsIntervention() ||
                getSignificantDelayCount() > incidents.size() * 0.3;
    }
}