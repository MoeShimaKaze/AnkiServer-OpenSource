package com.server.anki.mailorder.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.shopping.enums.MerchantLevel;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 邮件订单实体类
 * 实现了 FeeableOrder 接口以支持统一的费用计算系统
 * 实现了 Timeoutable 接口以支持统一的超时管理系统
 * 所有的数值类型字段都使用包装类型以支持 NULL 值和更好的领域模型表达
 */
@Entity
@Table(name = "mail_order")
public class MailOrder implements FeeableOrder, Timeoutable {

    // 基本订单信息
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @Column(name = "order_number", unique = true, nullable = false)
    private UUID orderNumber;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Setter
    @Getter
    @Column(name = "name")
    private String name;

    // 订单状态相关
    @Setter
    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeout_status")
    private TimeoutStatus timeoutStatus;

    @Getter
    @Column(name = "timeout_warning_sent")
    private Boolean timeoutWarningSent;

    @Column(name = "timeout_count", nullable = false)
    private Integer timeoutCount;

    // 用户信息
    @Setter
    @Getter
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Setter
    @Getter
    @ManyToOne
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    // 取件信息
    @Setter
    @Getter
    @Column(name = "pickup_code")
    private String pickupCode;

    @Setter
    @Getter
    @Column(name = "pickup_address")
    private String pickupAddress;

    @Setter
    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Setter
    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Setter
    @Getter
    @Column(name = "pickup_detail")
    private String pickupDetail;

    // 配送信息
    @Setter
    @Getter
    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Setter
    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Setter
    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Setter
    @Getter
    @Column(name = "delivery_detail")
    private String deliveryDetail;

    @Setter
    @Column(name = "delivery_distance")
    private Double deliveryDistance;

    @Setter
    @Getter
    @Column(name = "tracking_number")
    private String trackingNumber;

    @Setter
    @Getter
    @Column(name = "contact_info")
    private String contactInfo;

    // 时间相关
    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "delivery_time")
    private LocalDateTime deliveryTime;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "delivered_date")
    private LocalDateTime deliveredDate;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "intervention_time")
    private LocalDateTime interventionTime;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Setter
    @Getter
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "refund_date")
    private LocalDateTime refundDate;

    // 服务类型
    @Setter
    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_service")
    private DeliveryService deliveryService;

    // 商品信息 - 使用包装类型以支持空值
    @Setter
    @Column(name = "weight")
    private Double weight;

    @Column(name = "is_large_item")
    private boolean isLargeItem;  // 改为基本类型 boolean

    // 费用信息 - 使用包装类型以便更好地处理精度和空值
    @Setter
    @Getter
    @Column(name = "fee")
    private Double fee;

    @Setter
    @Getter
    @Column(name = "user_income")
    private Double userIncome;

    @Setter
    @Getter
    @Column(name = "platform_income")
    private Double platformIncome;

    @Setter
    @Getter
    @Column(name = "region_multiplier")
    private Double regionMultiplier = 1.0;

    // 其他信息
    @Setter
    @Getter
    @Column(name = "lock_reason")
    private String lockReason;


    // 构造方法
    public MailOrder() {
        this.orderStatus = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.orderNumber = UUID.randomUUID();
        this.timeoutStatus = TimeoutStatus.NORMAL;
        this.timeoutWarningSent = false;
        this.timeoutCount = 0;
    }

    // FeeableOrder 接口实现
    @Override
    public FeeType getFeeType() {
        return FeeType.MAIL_ORDER;
    }

    @Override
    public LocalDateTime getCreatedTime() {
        return this.createdAt;
    }

    @Override
    public Double getWeight() {
        return this.weight;
    }

    @Override
    public BigDecimal getProductPrice() {
        return BigDecimal.ZERO;
    }

    @Override
    public Integer getQuantity() {
        return 1;
    }

    @Override
    public BigDecimal getExpectedPrice() {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean hasMerchant() {
        return false;
    }

    @Override
    public MerchantLevel getMerchantLevel() {
        return null;
    }

    @Override
    public LocalDateTime getExpectedDeliveryTime() {
        return this.deliveryTime;
    }

    @Override
    public LocalDateTime getDeliveredTime() {
        return this.deliveredDate;
    }

    @Override
    public boolean needsInsurance() {
        return false;
    }

    @Override
    public BigDecimal getDeclaredValue() {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean hasSignatureService() {
        return false;
    }

    @Override
    public boolean hasPackagingService() {
        return false;
    }

    @Override
    public BigDecimal getDeliveryIncome() {
        return userIncome != null ? BigDecimal.valueOf(this.userIncome) : BigDecimal.ZERO;
    }

    @Override
    public boolean isStandardDelivery() {
        return this.deliveryService == DeliveryService.STANDARD;
    }

    @Override
    public boolean isLargeItem() {  // 返回类型使用基本类型 boolean
        return this.isLargeItem;
    }

    public void setLargeItem(Boolean largeItem) {
        isLargeItem = largeItem != null ? largeItem : false;
    }

    @Override
    public Double getPickupLatitude() {
        return this.pickupLatitude;
    }

    @Override
    public Double getPickupLongitude() {
        return this.pickupLongitude;
    }

    @Override
    public Double getDeliveryLatitude() {
        return this.deliveryLatitude;
    }

    @Override
    public Double getDeliveryLongitude() {
        return this.deliveryLongitude;
    }

    @Override
    public Double getDeliveryDistance() {
        return this.deliveryDistance;
    }

    // Timeoutable 接口实现
    @Override
    public TimeoutOrderType getTimeoutOrderType() {
        return TimeoutOrderType.MAIL_ORDER;
    }

    @Override
    public TimeoutStatus getTimeoutStatus() {
        return this.timeoutStatus;
    }

    @Override
    public boolean isTimeoutWarningSent() {
        return this.timeoutWarningSent != null && this.timeoutWarningSent;
    }

    @Override
    public int getTimeoutCount() {
        return this.timeoutCount != null ? this.timeoutCount : 0;
    }

    @Override
    public void setTimeoutStatus(TimeoutStatus status) {
        this.timeoutStatus = status;
    }

    @Override
    public void setTimeoutWarningSent(boolean sent) {
        this.timeoutWarningSent = sent;
    }

    @Override
    public void setTimeoutCount(int count) {
        this.timeoutCount = count;
    }

    @Override
    public LocalDateTime getInterventionTime() {
        return this.interventionTime;
    }

    @Override
    public void setInterventionTime(LocalDateTime time) {
        this.interventionTime = time;
    }

    @PrePersist
    protected void onCreate() {
        if (this.orderNumber == null) {
            this.orderNumber = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.orderStatus == null) {
            this.orderStatus = OrderStatus.PENDING;
        }
        if (this.timeoutStatus == null) {
            this.timeoutStatus = TimeoutStatus.NORMAL;
        }
        if (this.timeoutWarningSent == null) {
            this.timeoutWarningSent = false;
        }
        if (this.timeoutCount == null) {
            this.timeoutCount = 0;
        }
        if (this.regionMultiplier == null) {
            this.regionMultiplier = 1.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // 任何需要在更新前处理的逻辑
    }
}