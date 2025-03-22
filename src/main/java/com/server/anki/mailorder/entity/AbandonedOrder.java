package com.server.anki.mailorder.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.rating.RatingType;
import com.server.anki.marketing.SpecialDateType;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Table(name = "abandoned_order")
@Entity
@Getter
@Setter
public class AbandonedOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, nullable = false)
    private UUID orderNumber;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 基本信息字段
    @Column(name = "name")
    private String name;

    @Column(name = "pickup_code")
    private String pickupCode;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "contact_info")
    private String contactInfo;

    // 取件地址信息
    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "pickup_detail")
    private String pickupDetail;

    // 配送地址信息
    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(name = "delivery_detail")
    private String deliveryDetail;

    // 时间相关字段
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "delivery_time")
    private LocalDateTime deliveryTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "intervention_time")
    private LocalDateTime interventionTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "refund_date")
    private LocalDateTime refundDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "delivered_date")
    private LocalDateTime deliveredDate;

    // 订单状态和配送信息
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_service")
    private DeliveryService deliveryService;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeout_status")
    private TimeoutStatus timeoutStatus;

    @Column(name = "timeout_warning_sent")
    private Boolean timeoutWarningSent = false;

    // 费用相关字段
    @Column(name = "weight")
    private double weight;

    @Column(name = "is_large_item")
    private boolean isLargeItem;

    @Column(name = "fee")
    private double fee;

    @Column(name = "user_income")
    private double userIncome;

    @Column(name = "platform_income")
    private double platformIncome;

    // 锁定相关
    @Column(name = "lock_reason")
    private String lockReason;

    // 配送区域信息
    @Column(name = "region_multiplier")
    private double regionMultiplier = 1.0;

    @Column(name = "delivery_distance", precision = 10)
    private Double deliveryDistance;

    @Column(name = "pickup_region_name")
    private String pickupRegionName;

    @Column(name = "delivery_region_name")
    private String deliveryRegionName;

    @Column(name = "is_cross_region")
    private boolean isCrossRegion;

    // 时段费率信息
    @Column(name = "time_range_name")
    private String timeRangeName;

    @Column(name = "time_range_rate")
    private BigDecimal timeRangeRate;

    // 特殊日期信息
    @Column(name = "special_date_name")
    private String specialDateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "special_date_type")
    private SpecialDateType specialDateType;

    @Column(name = "special_date_rate")
    private BigDecimal specialDateRate;

    // 评价相关字段
    @ManyToOne
    @JoinColumn(name = "rater_id")
    private User rater;

    @ManyToOne
    @JoinColumn(name = "rated_user_id")
    private User ratedUser;

    @Column(name = "rating_comment", length = 1000)
    private String ratingComment;

    @Column(name = "rating_score")
    private Integer ratingScore;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "rating_date")
    private LocalDateTime ratingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type")
    private RatingType ratingType;

    @ManyToOne
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "timeout_count", nullable = false)
    private int timeoutCount = 0;  // 默认值为0

    // 构造函数
    public AbandonedOrder() {}
}