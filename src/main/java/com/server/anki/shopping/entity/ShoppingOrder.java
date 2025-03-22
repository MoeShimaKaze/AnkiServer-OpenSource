package com.server.anki.shopping.entity;

import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.MerchantLevel;
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
 * 商品订单实体类
 * 实现FeeableOrder接口以支持统一费用计算框架
 * 实现Timeoutable接口以支持统一超时管理框架
 */
@Entity
@Table(name = "shopping_order")
@Getter
@Setter
public class ShoppingOrder implements FeeableOrder, Timeoutable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", unique = true, nullable = false)
    private UUID orderNumber;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "product_volume")
    private Double productVolume;

    @Column(name = "product_price", nullable = false)
    private BigDecimal productPrice;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "service_fee", nullable = false)
    private BigDecimal serviceFee;

    @Column(name = "platform_fee", nullable = false)
    private BigDecimal platformFee;

    @Column(name = "merchant_income", nullable = false)
    private BigDecimal merchantIncome;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private DeliveryType deliveryType;

    @Convert(converter = OrderStatusStringConverter.class) // 添加这一行
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    @Column(name = "delivery_distance")
    private Double deliveryDistance;

    @ManyToOne
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "expected_delivery_time")
    private LocalDateTime expectedDeliveryTime;

    @Column(name = "delivered_time")
    private LocalDateTime deliveredTime;

    @Column(name = "remark")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "refund_time")
    private LocalDateTime refundTime;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_reason")
    private String refundReason;

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

    public void setProduct(Product product) {
        this.product = product;
        calculateProductVolume();
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        calculateProductVolume();
    }

    private void calculateProductVolume() {
        if (this.product != null && this.quantity != null) {
            Double singleVolume = this.product.calculateVolume();
            this.productVolume = singleVolume != null ? singleVolume * this.quantity : null;
        } else {
            this.productVolume = null;
        }
    }

    // FeeableOrder 接口实现
    @Override
    public UUID getOrderNumber() {
        return this.orderNumber;
    }

    @Override
    public FeeType getFeeType() {
        return FeeType.SHOPPING_ORDER;
    }

    @Override
    public LocalDateTime getCreatedTime() {
        return this.createdAt;
    }

    @Override
    public Double getPickupLatitude() {
        return this.store.getLatitude();
    }

    @Override
    public Double getPickupLongitude() {
        return this.store.getLongitude();
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
        return this.product.getWeight() * this.quantity;
    }

    @Override
    public boolean isLargeItem() {
        return this.product.isLargeItem();
    }

    @Override
    public BigDecimal getProductPrice() {
        return this.productPrice;
    }

    @Override
    public Integer getQuantity() {
        return this.quantity;
    }

    @Override
    public BigDecimal getExpectedPrice() {
        return this.productPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean hasMerchant() {
        return true;
    }

    @Override
    public MerchantLevel getMerchantLevel() {
        return this.store.getMerchantInfo().getMerchantLevel();
    }

    @Override
    public LocalDateTime getExpectedDeliveryTime() {
        return this.expectedDeliveryTime;
    }

    @Override
    public LocalDateTime getDeliveredTime() {
        return this.deliveredTime;
    }

    @Override
    public boolean needsInsurance() {
        return this.getExpectedPrice().compareTo(BigDecimal.valueOf(1000)) > 0;
    }

    @Override
    public BigDecimal getDeclaredValue() {
        return this.getExpectedPrice();
    }

    @Override
    public boolean hasSignatureService() {
        return true;
    }

    @Override
    public boolean hasPackagingService() {
        return this.product.needsPackaging();
    }

    @Override
    public BigDecimal getDeliveryIncome() {
        return this.deliveryFee;
    }

    @Override
    public boolean isStandardDelivery() {
        return this.deliveryType == DeliveryType.MUTUAL;
    }

    public void updateFeeResult(FeeResult feeResult) {
        this.deliveryFee = feeResult.getDeliveryFee();
        this.serviceFee = feeResult.getServiceFee();
        this.platformFee = feeResult.getDistribution().getPlatformIncome();
        this.merchantIncome = feeResult.getDistribution().getMerchantIncome();
        this.deliveryDistance = feeResult.getDeliveryDistance();

        BigDecimal totalProductAmount = this.productPrice.multiply(BigDecimal.valueOf(this.quantity));
        this.totalAmount = totalProductAmount.add(feeResult.getTotalFee());
    }

    /**
     * 计算可退款金额
     * 根据订单状态和配送进度计算可退款的具体金额
     * 退款规则：
     * 1. 未配送：全额退款（商品费用 + 配送费）
     * 2. 已分配配送员但未开始配送：全额退款，扣除10%手续费
     * 3. 配送中：
     *    - 退还商品费用
     *    - 扣除配送费
     *    - 扣除服务费
     *    - 如果是大件商品额外扣除处理费
     * 4. 其他状态：不可退款
     *
     * @return 可退款金额
     */
    public BigDecimal calculateRefundableAmount() {
        // 商品总价
        BigDecimal productTotal = this.productPrice.multiply(BigDecimal.valueOf(this.quantity));

        // 根据订单状态计算退款金额
        switch (this.orderStatus) {
            case PENDING -> {
                // 未配送，全额退款
                return this.totalAmount;
            }

            case ASSIGNED -> {
                // 已分配配送员但未开始配送，收取10%手续费
                BigDecimal handlingFee = this.totalAmount.multiply(BigDecimal.valueOf(0.1));
                return this.totalAmount.subtract(handlingFee);
            }

            case IN_TRANSIT -> {
                if (this.deliveredTime != null) {
                    // 已送达不可退款
                    return BigDecimal.ZERO;
                }

                // 退还商品费用，扣除配送相关费用
                BigDecimal refundAmount = productTotal;

                // 扣除配送费
                refundAmount = refundAmount.subtract(this.deliveryFee);

                // 扣除服务费
                refundAmount = refundAmount.subtract(this.serviceFee);

                // 大件商品额外扣除处理费
                if (this.product.isLargeItem()) {
                    BigDecimal handlingFee = productTotal.multiply(BigDecimal.valueOf(0.05));
                    refundAmount = refundAmount.subtract(handlingFee);
                }

                return refundAmount.max(BigDecimal.ZERO);
            }

            default -> {
                // 其他状态不可退款
                return BigDecimal.ZERO;
            }
        }
    }

    // Timeoutable 接口实现
    @Override
    public TimeoutOrderType getTimeoutOrderType() {
        return TimeoutOrderType.SHOPPING_ORDER;
    }

    @PrePersist
    protected void onCreate() {
        if (this.orderNumber == null) {
            this.orderNumber = UUID.randomUUID();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // 只在状态为null时设置默认值
        if (this.orderStatus == null) {
            this.orderStatus = OrderStatus.PAYMENT_PENDING;
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

    public boolean isRefundable() {
        return this.orderStatus == OrderStatus.PENDING ||
                this.orderStatus == OrderStatus.ASSIGNED ||
                (this.orderStatus == OrderStatus.IN_TRANSIT &&
                        this.deliveredTime == null);
    }

    public void updateRefundStatus(String status, String reason) {
        this.refundStatus = status;
        this.refundReason = reason;
        if ("APPROVED".equals(status)) {
            this.refundTime = LocalDateTime.now();
        }
    }
}