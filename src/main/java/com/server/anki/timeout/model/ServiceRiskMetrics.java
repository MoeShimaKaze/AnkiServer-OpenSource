package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 服务风险指标
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record ServiceRiskMetrics(
        double timeoutRate,
        double averageDelay,
        double severityScore
) {
    /**
     * 获取风险等级
     */
    @JsonIgnore
    public RiskLevel getRiskLevel() {
        if (severityScore >= 80) return RiskLevel.CRITICAL;
        if (severityScore >= 60) return RiskLevel.HIGH;
        if (severityScore >= 40) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /**
     * 判断是否需要干预
     */
    @JsonIgnore
    public boolean needsIntervention() {
        return timeoutRate > 15.0 || averageDelay > 60.0 || severityScore > 70.0;
    }

    /**
     * 获取建议的措施
     */
    @JsonIgnore
    public String getRecommendedAction() {
        return switch (getRiskLevel()) {
            case CRITICAL -> "立即进行人工干预，暂停新订单分配";
            case HIGH -> "增加人员配置，优化配送路线";
            case MEDIUM -> "密切监控，准备应急预案";
            case LOW -> "保持正常运营";
        };
    }
}