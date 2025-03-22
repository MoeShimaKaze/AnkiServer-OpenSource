package com.server.anki.shopping.entity;

import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.MerchantLevel;
import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.user.User;
import com.server.anki.utils.OrderStatusStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 代购需求实体类
 * 实现了FeeableOrder接口以支持统一的费用计算框架
 * 实现了Timeoutable接口以支持统一的超时管理框架
 * 记录代购需求的详细信息，包括商品信息、配送信息和支付信息
 */
@Entity
@Table(name = "purchase_request")
@Getter
@Setter
public class PurchaseRequest implements FeeableOrder, Timeoutable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 添加设置请求编号的方法
    // 如果 PurchaseRequest 类中没有此方法，则添加它
    @Setter
    @Getter
    @Column(name = "request_number", unique = true, nullable = false)
    private UUID requestNumber;

    // 用户信息
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 基本信息
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ProductCategory category;

    @Column(name = "expected_price", nullable = false)
    private BigDecimal expectedPrice;

    @Column(name = "image_url")
    private String imageUrl;

    // 代购地址信息
    @Column(name = "purchase_address", nullable = false)
    private String purchaseAddress;

    @Column(name = "purchase_latitude", nullable = false)
    private Double purchaseLatitude;

    @Column(name = "purchase_longitude", nullable = false)
    private Double purchaseLongitude;

    // 配送地址信息
    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(name = "delivery_distance")
    private Double deliveryDistance;

    // 收件人信息
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    // 配送相关
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private DeliveryType deliveryType;

    @ManyToOne
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    // 时间信息
    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "delivery_time")
    private LocalDateTime deliveryTime;

    @Column(name = "delivered_date")
    private LocalDateTime deliveredDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    // 状态信息
    @Convert(converter = OrderStatusStringConverter.class) // 添加这一行
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    // 费用信息
    @Column(name = "delivery_fee")
    private BigDecimal deliveryFee;

    @Column(name = "service_fee")
    private BigDecimal serviceFee;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "user_income")
    private double userIncome;

    @Column(name = "platform_income")
    private double platformIncome;

    // 退款信息
    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_date")
    private LocalDateTime refundDate;

    // 超时管理相关字段
    @Enumerated(EnumType.STRING)
    @Column(name = "timeout_status")
    private TimeoutStatus timeoutStatus = TimeoutStatus.NORMAL;

    @Column(name = "timeout_warning_sent")
    private boolean timeoutWarningSent = false;

    @Column(name = "timeout_count", nullable = false)
    private int timeoutCount = 0;

    @Column(name = "intervention_time")
    private LocalDateTime interventionTime;

    @Column(name = "weight")
    private Double weight; // 添加重量字段存储用户输入的重量

    // 添加浏览量字段
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    // FeeableOrder 接口实现
    @Override
    public UUID getOrderNumber() {
        return this.requestNumber;
    }

    @Override
    public FeeType getFeeType() {
        return FeeType.PURCHASE_ORDER;
    }

    @Override
    public LocalDateTime getCreatedTime() {
        return this.createdAt;
    }

    @Override
    public Double getPickupLatitude() {
        return this.purchaseLatitude;
    }

    @Override
    public Double getPickupLongitude() {
        return this.purchaseLongitude;
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

    @Override
    public Double getWeight() {
        // 如果前端提交了重量，优先使用前端提交的重量
        if (this.weight != null && this.weight > 0) {
            return this.weight;
        }

        // 否则根据商品类别设置默认重量
        if (this.category != null) {
            return switch (this.category) {
                case ELECTRONICS -> 2.0; // 电子产品默认2公斤
                case BOOKS -> 1.5; // 图书默认1.5公斤
                case FOOD -> 1.0; // 食品默认1公斤
                case CLOTHING -> 0.5; // 衣物默认0.5公斤
                case MEDICINE -> 0.8; // 药品默认0.8公斤
                case DAILY_NECESSITIES -> 1.0; // 日用品默认1公斤
                case BEAUTY -> 0.8; // 美妆默认0.8公斤
                case SPORTS -> 2.0; // 运动产品默认2公斤
                default -> 1.0; // 其他类别默认1公斤
            };
        }
        return 1.0; // 未指定类别时默认1公斤
    }

    @Override
    public boolean isLargeItem() {
        // 代购订单默认不是大件
        return false;
    }

    @Override
    public BigDecimal getProductPrice() {
        return this.expectedPrice;
    }

    @Override
    public Integer getQuantity() {
        // 代购订单默认数量为1
        return 1;
    }

    @Override
    public BigDecimal getExpectedPrice() {
        return this.expectedPrice;
    }

    @Override
    public boolean hasMerchant() {
        // 代购订单没有商家
        return false;
    }

    @Override
    public MerchantLevel getMerchantLevel() {
        // 代购订单没有商家等级
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
        // 1000元以上商品需要保险
        return this.expectedPrice.compareTo(BigDecimal.valueOf(1000)) > 0;
    }

    @Override
    public BigDecimal getDeclaredValue() {
        return this.expectedPrice;
    }

    @Override
    public boolean hasSignatureService() {
        // 代购订单默认不需要签名
        return false;
    }

    @Override
    public boolean hasPackagingService() {
        // 代购订单默认需要包装
        return true;
    }

    @Override
    public BigDecimal getDeliveryIncome() {
        return BigDecimal.valueOf(this.userIncome);
    }

    @Override
    public boolean isStandardDelivery() {
        return this.deliveryType == DeliveryType.MUTUAL;
    }

    // Timeoutable 接口实现
    @Override
    public OrderStatus getOrderStatus() {
        return this.status;
    }

    @Override
    public void setOrderStatus(OrderStatus status) {
        this.status = status;
    }

    @Override
    public TimeoutOrderType getTimeoutOrderType() {
        return TimeoutOrderType.PURCHASE_REQUEST;
    }

    /**
     * 计算总金额
     * 总金额 = 预期价格 + 配送费 + 服务费
     */
    public void calculateTotalAmount() {
        this.totalAmount = this.expectedPrice
                .add(this.deliveryFee)
                .add(this.serviceFee);
    }

    /**
     * 计算可退款金额
     * 根据订单状态和配送进度计算可退款金额
     */
    public BigDecimal calculateRefundableAmount() {
        if (this.status == OrderStatus.PENDING ||
                this.status == OrderStatus.PAYMENT_PENDING) {
            // 未开始配送，可全额退款
            return this.totalAmount;
        } else if (this.status == OrderStatus.ASSIGNED) {
            // 已接单但未购买商品，扣除服务费退款
            return this.totalAmount.subtract(this.serviceFee);
        } else if (this.status == OrderStatus.IN_TRANSIT &&
                this.deliveredDate == null) {
            // 配送中但未送达，扣除服务费和配送费
            return this.totalAmount.subtract(this.serviceFee)
                    .subtract(this.deliveryFee);
        }
        // 其他状态不可退款
        return BigDecimal.ZERO;
    }

    /**
     * 检查是否可以申请退款
     */
    public boolean isRefundable() {
        return this.status == OrderStatus.PENDING ||
                this.status == OrderStatus.PAYMENT_PENDING ||
                this.status == OrderStatus.ASSIGNED ||
                (this.status == OrderStatus.IN_TRANSIT &&
                        this.deliveredDate == null);
    }

// PurchaseRequest.java中的@PrePersist方法修改

    @PrePersist
    protected void onCreate() {
        if (this.requestNumber == null) {
            this.requestNumber = UUID.randomUUID();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // 只在状态为null时设置默认值
        if (this.status == null) {
            this.status = OrderStatus.PAYMENT_PENDING;
        }

        // 检查并设置其他引用类型字段的默认值
        if (this.timeoutStatus == null) {
            this.timeoutStatus = TimeoutStatus.NORMAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}