package com.server.anki.marketing.entity;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.SpecialDateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "special_date")
@Getter
@Setter
public class SpecialDate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;  // 名称（如"元旦"、"双11促销"）

    @Column(name = "date", nullable = false)
    private LocalDate date;  // 日期

    @Column(name = "rate_multiplier", nullable = false)
    private BigDecimal rateMultiplier;  // 费率倍数

    @Column(name = "description")
    private String description;  // 描述

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SpecialDateType type;  // 类型（节假日/营销活动）

    @Column(name = "active", nullable = false)
    private boolean active = true;  // 是否启用

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "rate_enabled", nullable = false)
    private boolean rateEnabled = true;  // 费率是否启用

    @Column(name = "priority", nullable = false)
    private int priority = 0;  // 优先级，当同一天有多个特殊日期时使用

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
