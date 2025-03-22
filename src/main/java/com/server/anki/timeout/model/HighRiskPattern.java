package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 高风险模式
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record HighRiskPattern(
        String pattern,
        int occurrence,
        double riskLevel,
        String description
) {
    @JsonIgnore
    public boolean isUrgent() {
        return riskLevel > 0.8 && occurrence > 5;
    }

    @JsonIgnore
    public String getRiskLevelDescription() {
        if (riskLevel < 0.3) return "低风险";
        if (riskLevel < 0.6) return "中等风险";
        if (riskLevel < 0.8) return "高风险";
        return "极高风险";
    }
}