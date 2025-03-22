package com.server.anki.timeout.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.server.anki.mailorder.enums.DeliveryService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 超时事件记录
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public record TimeoutIncident(
        UUID orderNumber,
        LocalDateTime timestamp,
        String type,
        BigDecimal fee,
        DeliveryService service,
        long delayMinutes
) {
    @JsonIgnore
    public boolean isSignificantDelay() {
        return delayMinutes > 60; // 超过1小时的延迟被认为是显著延迟
    }

    @JsonIgnore
    public boolean isHighFee() {
        return fee.compareTo(BigDecimal.valueOf(50)) > 0; // 超过50的费用被认为是高额费用
    }

    /**
     * 获取延迟程度描述
     */
    @JsonIgnore
    public String getDelayLevel() {
        if (delayMinutes <= 30) return "轻微延迟";
        if (delayMinutes <= 60) return "中等延迟";
        if (delayMinutes <= 120) return "严重延迟";
        return "极度延迟";
    }
}