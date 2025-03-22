package com.server.anki.shopping.controller.response;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代购需求响应类
 * 用于向前端返回代购需求相关信息，包含完整的支付和配送信息
 */
@Data
@Builder
public class PurchaseRequestResponse {
    // 基本信息
    private Long id;
    private String requestNumber;    // 需求编号
    private Long userId;
    private String username;
    private String title;
    private String description;
    private ProductCategory category;

    // 价格信息
    private BigDecimal expectedPrice;    // 商品预期价格
    private BigDecimal deliveryFee;      // 配送费用
    private BigDecimal totalAmount;      // 总金额

    // 添加代购地址相关字段
    private String purchaseAddress;     // 代购地址
    private Double purchaseLatitude;    // 代购地址纬度
    private Double purchaseLongitude;   // 代购地址经度

    // 配送信息
    private DeliveryType deliveryType;   // 配送方式
    private String deliveryAddress;      // 配送地址
    private Double deliveryLatitude;     // 配送地址纬度
    private Double deliveryLongitude;    // 配送地址经度
    private String recipientName;        // 收货人姓名
    private String recipientPhone;       // 收货人电话
    private Long assignedUserId;         // 配送员ID
    private String assignedUserName;     // 配送员姓名

    // 时间信息
    private LocalDateTime deadline;           // 截止时间
    private LocalDateTime paymentTime;        // 支付时间
    private LocalDateTime completionDate;     // 完成时间
    private LocalDateTime deliveredDate;      // 送达时间
    private LocalDateTime createdAt;          // 创建时间
    private LocalDateTime updatedAt;          // 更新时间

    // 状态信息
    private OrderStatus status;           // 订单状态
    private String refundStatus;         // 退款状态
    private BigDecimal refundAmount;     // 退款金额

    private String imageUrl;             // 商品参考图
    private Boolean hasExpired;          // 是否已过期
    private Boolean canAccept;           // 是否可以接单
    private Boolean canRefund;           // 是否可以退款
    private int viewCount; // 添加浏览量字段

    /**
     * 将PurchaseRequest实体转换为响应对象
     */
    public static PurchaseRequestResponse fromRequest(PurchaseRequest request) {
        return PurchaseRequestResponse.builder()
                .id(request.getId())
                .requestNumber(request.getRequestNumber().toString())
                .userId(request.getUser().getId())
                .username(request.getUser().getUsername())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .expectedPrice(request.getExpectedPrice())
                .deliveryFee(request.getDeliveryFee())
                .totalAmount(request.getTotalAmount())
                // 添加代购地址字段映射
                .purchaseAddress(request.getPurchaseAddress())
                .purchaseLatitude(request.getPurchaseLatitude())
                .purchaseLongitude(request.getPurchaseLongitude())
                .deliveryType(request.getDeliveryType())
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .recipientName(request.getRecipientName())
                .recipientPhone(request.getRecipientPhone())
                .assignedUserId(request.getAssignedUser() != null ? request.getAssignedUser().getId() : null)
                .assignedUserName(request.getAssignedUser() != null ? request.getAssignedUser().getUsername() : null)
                .deadline(request.getDeadline())
                .paymentTime(request.getPaymentTime())
                .completionDate(request.getCompletionDate())
                .deliveredDate(request.getDeliveredDate())
                .status(request.getStatus())
                .refundStatus(request.getRefundStatus())
                .refundAmount(request.getRefundAmount())
                .imageUrl(request.getImageUrl())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .hasExpired(hasRequestExpired(request))
                .canAccept(canAcceptRequest(request))
                .canRefund(request.isRefundable())
                .viewCount(request.getViewCount()) // 添加浏览量
                .build();
    }

    /**
     * 判断需求是否已过期
     * 检查截止时间或订单是否已经完成/取消
     */
    private static Boolean hasRequestExpired(PurchaseRequest request) {
        return request.getDeadline().isBefore(LocalDateTime.now()) ||
                request.getStatus() == OrderStatus.COMPLETED ||
                request.getStatus() == OrderStatus.CANCELLED;
    }

    /**
     * 判断需求是否可以接单
     * 只有已支付且未分配配送员的订单可以接单
     */
    private static Boolean canAcceptRequest(PurchaseRequest request) {
        return request.getStatus() == OrderStatus.PENDING &&
                request.getAssignedUser() == null &&
                !hasRequestExpired(request);
    }
}