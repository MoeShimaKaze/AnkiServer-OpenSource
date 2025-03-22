package com.server.anki.shopping.dto;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.enums.DeliveryType;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ShoppingOrderDTO {
    private Long id;
    private UUID orderNumber;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "店铺ID不能为空")
    private Long storeId;

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量必须大于0")
    private Integer quantity;

    private BigDecimal productPrice;
    private BigDecimal totalAmount;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFee;
    private BigDecimal platformFee;
    private BigDecimal merchantIncome;

    @NotNull(message = "配送方式不能为空")
    private DeliveryType deliveryType;

    private OrderStatus orderStatus;

    @NotBlank(message = "收货人姓名不能为空")
    private String recipientName;

    @NotBlank(message = "收货人电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人电话格式不正确")
    private String recipientPhone;

    @NotBlank(message = "配送地址不能为空")
    private String deliveryAddress;

    @NotNull(message = "配送地址纬度不能为空")
    private Double deliveryLatitude;

    @NotNull(message = "配送地址经度不能为空")
    private Double deliveryLongitude;

    private Long assignedUserId;  // 配送员ID
    private String assignedUserName;  // 配送员姓名，用于展示

    private LocalDateTime expectedDeliveryTime;
    private LocalDateTime deliveredTime;
    private String remark;

    // 支付相关信息
    private LocalDateTime paymentTime;
    private String refundStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;

    // 用于展示的附加信息
    private String storeName;
    private String productName;
    private String productImage;
}