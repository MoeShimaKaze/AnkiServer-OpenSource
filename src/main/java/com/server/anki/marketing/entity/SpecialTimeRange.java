package com.server.anki.marketing.entity;

import com.server.anki.fee.model.FeeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "special_time_range")
@Getter
@Setter
public class SpecialTimeRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;  // 时段名称（如"夜间配送"、"早高峰"）

    @Column(name = "start_hour", nullable = false)
    private int startHour;  // 开始时间（小时）

    @Column(name = "end_hour", nullable = false)
    private int endHour;    // 结束时间（小时）

    @Column(name = "rate_multiplier", nullable = false)
    private BigDecimal rateMultiplier = BigDecimal.ONE;  // 费率倍数

    @Column(name = "description")
    private String description;  // 描述

    @Column(name = "active", nullable = false)
    private boolean active = true;  // 是否启用

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}