package com.server.anki.shopping.dto;

import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.ProductCategory;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代购需求传输对象
 * 用于前端创建和更新代购需求时传递数据
 */
@Data
public class PurchaseRequestDTO {
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String username;  // 用户名，用于展示

    @NotBlank(message = "需求标题不能为空")
    @Size(min = 5, max = 100, message = "需求标题长度必须在5-100个字符之间")
    private String title;

    @NotBlank(message = "需求描述不能为空")
    @Size(max = 1000, message = "需求描述不能超过1000个字符")
    private String description;

    @NotNull(message = "商品分类不能为空")
    private ProductCategory category;

    @NotNull(message = "预期价格不能为空")
    @DecimalMin(value = "0.01", message = "预期价格必须大于0")
    private BigDecimal expectedPrice;

    private String imageUrl;  // 商品参考图URL

    @NotNull(message = "需求截止时间不能为空")
    @Future(message = "需求截止时间必须是将来时间")
    private LocalDateTime deadline;

    @NotBlank(message = "代购地址不能为空")
    private String purchaseAddress;

    @NotNull(message = "代购地址纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度范围必须在-90到90之间")
    @DecimalMax(value = "90", message = "纬度范围必须在-90到90之间")
    private Double purchaseLatitude;

    @NotNull(message = "代购地址经度不能为空")
    @DecimalMin(value = "-180", message = "经度范围必须在-180到180之间")
    @DecimalMax(value = "180", message = "经度范围必须在-180到180之间")
    private Double purchaseLongitude;

    // 新增配送相关字段
    @NotNull(message = "配送方式不能为空")
    private DeliveryType deliveryType;

    @NotBlank(message = "收货地址不能为空")
    private String deliveryAddress;

    @NotNull(message = "收货地址纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度范围必须在-90到90之间")
    @DecimalMax(value = "90", message = "纬度范围必须在-90到90之间")
    private Double deliveryLatitude;

    @NotNull(message = "收货地址经度不能为空")
    @DecimalMin(value = "-180", message = "经度范围必须在-180到180之间")
    @DecimalMax(value = "180", message = "经度范围必须在-180到180之间")
    private Double deliveryLongitude;

    @NotBlank(message = "收货人姓名不能为空")
    private String recipientName;

    @NotBlank(message = "收货人电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人电话格式不正确")
    private String recipientPhone;

    @DecimalMin(value = "0.1", message = "重量必须大于0.1公斤")
    @DecimalMax(value = "50.0", message = "重量不能超过50公斤")
    private Double weight; // 新增的重量字段

     private String status;

     private LocalDateTime createdAt;
     private LocalDateTime updatedAt;
}