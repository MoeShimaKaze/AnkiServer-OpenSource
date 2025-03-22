package com.server.anki.mailorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.server.anki.rating.RatingDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class MailOrderDTO {
    @Getter
    @Setter
    private UUID orderNumber;
    @Setter
    @Getter
    private String orderStatus;
    // 添加缺失的 getter 和 setter 方法
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private String pickupCode;
    @Setter
    @Getter
    private String trackingNumber;
    @Setter
    @Getter
    private String contactInfo;
    @Setter
    @Getter
    private String pickupAddress;
    @Setter
    @Getter
    private Double pickupLatitude;
    @Setter
    @Getter
    private Double pickupLongitude;
    @Setter
    @Getter
    private String pickupDetail;
    @Setter
    @Getter
    @JsonProperty("deliveryAddress")
    private String deliveryAddress;

    @Getter
    @Setter
    @JsonProperty("deliveryDetail")
    private String deliveryDetail;

    @Setter
    @Getter
    @JsonProperty("deliveryLatitude")
    private Double deliveryLatitude;

    @Setter
    @Getter
    @JsonProperty("deliveryLongitude")
    private Double deliveryLongitude;
    // 修改 deliveryTime 的类型和相关方法
    @Setter
    @Getter
    private LocalDateTime deliveryTime;
    // 修改 deliveryService 的类型和相关方法
    @Setter
    @Getter
    private String deliveryService;
    @Setter
    @Getter
    private double weight;
    private boolean isLargeItem;
    @Setter
    @Getter
    private double userIncome;
    @Setter
    @Getter
    private LocalDateTime createdAt;
    @Setter
    @Getter
    private LocalDateTime completionDate;
    @Setter
    @Getter
    private Long userId; // 新增
    @Setter
    @Getter
    private Long assignedUserId; // 新增
    @Setter
    @Getter
    private RatingDTO rating;

    @Setter
    @Getter
    private double platformIncome;

    @Setter
    @Getter
    private double regionMultiplier = 1.0;

    @Setter
    @Getter
    private Double deliveryDistance;

    @Setter
    @Getter
    private LocalDateTime deliveredDate;

    // 添加区域信息字段
    @Setter
    @Getter
    private String pickupRegionName;

    @Setter
    @Getter
    private String deliveryRegionName;

    @Setter
    @Getter
    private boolean isCrossRegion;

    // 添加时段信息字段
    @Setter
    @Getter
    private String timeRangeName;

    @Setter
    @Getter
    private BigDecimal timeRangeRate;

    // 添加特殊日期信息字段
    @Setter
    @Getter
    private String specialDateName;

    @Setter
    @Getter
    private String specialDateType;

    @Setter
    @Getter
    private BigDecimal specialDateRate;

    public boolean isLargeItem() {
        return isLargeItem;
    }

    public void setLargeItem(boolean largeItem) {
        isLargeItem = largeItem;
    }

}