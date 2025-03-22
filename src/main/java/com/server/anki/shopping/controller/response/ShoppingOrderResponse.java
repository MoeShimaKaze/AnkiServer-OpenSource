package com.server.anki.shopping.controller.response;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.enums.DeliveryType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 商品订单响应对象
 * 封装订单的完整信息用于前端展示
 */
@Data
@Builder
public class ShoppingOrderResponse {
    // 订单基本信息
    private UUID orderNumber;                 // 订单编号
    private OrderStatus orderStatus;          // 订单状态
    private LocalDateTime createdAt;          // 创建时间
    private LocalDateTime paymentTime;        // 支付时间

    // 用户信息
    private Long userId;                      // 用户ID
    private String userName;                  // 用户名称

    // 店铺信息
    private Long storeId;                     // 店铺ID
    private String storeName;                 // 店铺名称

    // 商品基本信息
    private Long productId;                   // 商品ID
    private String productName;               // 商品名称
    private String productCategory;           // 商品类别
    private Integer quantity;                 // 购买数量
    private BigDecimal productPrice;          // 商品单价

    // 商品物理属性
    private Double productWeight;             // 商品单重(kg)
    private Double totalWeight;               // 总重量(kg)
    private Double productVolume;             // 商品体积(m³)
    private boolean isLargeItem;              // 是否大件商品

    // 配送信息
    private DeliveryType deliveryType;        // 配送方式
    private Double deliveryDistance;          // 配送距离(km)
    private String recipientName;             // 收货人姓名
    private String recipientPhone;            // 收货人电话
    private String deliveryAddress;           // 配送地址
    private Double deliveryLatitude;          // 配送地址纬度
    private Double deliveryLongitude;         // 配送地址经度

    // 配送员信息
    private Long assignedUserId;              // 配送员ID
    private String assignedUserName;          // 配送员姓名

    // 费用信息
    private BigDecimal totalAmount;           // 订单总金额
    private BigDecimal deliveryFee;           // 配送费
    private BigDecimal serviceFee;            // 服务费
    private BigDecimal platformFee;           // 平台费用
    private BigDecimal merchantIncome;        // 商家收入

    // 配送时间信息
    private LocalDateTime expectedDeliveryTime; // 预计送达时间
    private LocalDateTime deliveredTime;        // 实际送达时间

    // 退款信息
    private String refundStatus;              // 退款状态
    private BigDecimal refundAmount;          // 退款金额
    private String refundReason;              // 退款原因

    /**
     * 将订单实体转换为响应对象
     * 整合订单的完整信息,包括商品、配送、费用等各个维度
     */
    public static ShoppingOrderResponse fromOrder(ShoppingOrder order) {
        return ShoppingOrderResponse.builder()
                // 订单基本信息
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .paymentTime(order.getPaymentTime())

                // 用户信息
                .userId(order.getUser().getId())
                .userName(order.getUser().getUsername())

                // 店铺信息
                .storeId(order.getStore().getId())
                .storeName(order.getStore().getStoreName())

                // 商品基本信息
                .productId(order.getProduct().getId())
                .productName(order.getProduct().getName())
                .productCategory(order.getProduct().getCategory().getLabel())
                .quantity(order.getQuantity())
                .productPrice(order.getProductPrice())

                // 商品物理属性
                .productWeight(order.getProduct().getWeight())
                .totalWeight(order.getProduct().getWeight() * order.getQuantity())
                .productVolume(order.getProductVolume())
                .isLargeItem(order.getProduct().isLargeItem())

                // 配送信息
                .deliveryType(order.getDeliveryType())
                .deliveryDistance(order.getDeliveryDistance())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())

                // 配送员信息(注意处理可能为null的情况)
                .assignedUserId(order.getAssignedUser() != null ?
                        order.getAssignedUser().getId() : null)
                .assignedUserName(order.getAssignedUser() != null ?
                        order.getAssignedUser().getUsername() : null)

                // 费用信息
                .totalAmount(order.getTotalAmount())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .platformFee(order.getPlatformFee())
                .merchantIncome(order.getMerchantIncome())

                // 配送时间信息
                .expectedDeliveryTime(order.getExpectedDeliveryTime())
                .deliveredTime(order.getDeliveredTime())

                // 退款信息
                .refundStatus(order.getRefundStatus())
                .refundAmount(order.getRefundAmount())
                .refundReason(order.getRefundReason())

                .build();
    }

    /**
     * 获取订单状态的中文描述
     */
    public String getOrderStatusDescription() {
        if (orderStatus == null) {
            return "未知状态";
        }

        return switch (orderStatus) {
            case PAYMENT_PENDING -> "待支付";
            case PENDING -> "待接单";
            case ASSIGNED -> "已接单";
            case IN_TRANSIT -> "配送中";
            case DELIVERED -> "已送达";
            case COMPLETED -> "已完成";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
            case PLATFORM_INTERVENTION -> "平台处理中";
            default -> orderStatus.toString();
        };
    }

    /**
     * 获取预计送达时间的格式化描述
     */
    public String getExpectedDeliveryTimeDescription() {
        if (expectedDeliveryTime == null) {
            return "待定";
        }
        return expectedDeliveryTime.format(
                DateTimeFormatter.ofPattern("MM-dd HH:mm")
        );
    }

    /**
     * 获取实际送达时间的格式化描述
     */
    public String getDeliveredTimeDescription() {
        if (deliveredTime == null) {
            return "未送达";
        }
        return deliveredTime.format(
                DateTimeFormatter.ofPattern("MM-dd HH:mm")
        );
    }

    /**
     * 获取配送距离的格式化描述
     */
    public String getDeliveryDistanceDescription() {
        if (deliveryDistance == null) {
            return "未知距离";
        }
        return String.format("%.1f公里", deliveryDistance);
    }

    /**
     * 检查订单是否可以退款
     */
    public boolean isRefundable() {
        // 仅待发货和配送中的订单可以申请退款
        return orderStatus == OrderStatus.PENDING ||
                orderStatus == OrderStatus.ASSIGNED ||
                (orderStatus == OrderStatus.IN_TRANSIT &&
                        deliveredTime == null);
    }
}