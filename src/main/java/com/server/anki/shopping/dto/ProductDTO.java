package com.server.anki.shopping.dto;

import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.enums.ProductStatus;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO {
    private Long id;

    @NotNull(message = "店铺ID不能为空")
    private Long storeId;

    @NotBlank(message = "商品名称不能为空")
    @Size(min = 2, max = 100, message = "商品名称长度必须在2-100个字符之间")
    private String name;

    @Size(max = 1000, message = "商品描述不能超过1000个字符")
    private String description;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;

    private BigDecimal costPrice;
    private BigDecimal marketPrice;
    private BigDecimal wholesalePrice;

    @NotNull(message = "商品库存不能为空")
    @Min(value = 0, message = "商品库存不能小于0")
    private Integer stock;

    @NotNull(message = "商品状态不能为空")
    private ProductStatus status;

    @NotNull(message = "商品分类不能为空")
    private ProductCategory category;

    private String imageUrl;  // 商品图片URL
    private String barcode;   // 商品条形码
    private String skuCode;   // 商品SKU编码

    @NotNull(message = "商品重量不能为空")
    @DecimalMin(value = "0.01", message = "商品重量必须大于0")
    private Double weight;

    private Double length;    // 长度(cm)
    private Double width;     // 宽度(cm)
    private Double height;    // 高度(cm)

    // 修改为符合调用方式的命名
    private Boolean isLargeItem = false;
    private Boolean isFragile = false;
    private Boolean needsPackaging = false;

    private Integer maxBuyLimit;      // 最大购买数量限制

    @Min(value = 1, message = "最小购买数量不能小于1")
    private Integer minBuyLimit = 1;  // 最小购买数量限制

    private Double rating = 5.0;          // 商品评分
    private Integer salesCount = 0;       // 销售数量
    private Integer viewCount = 0;        // 浏览次数

    private String storeName;  // 所属店铺名称，用于展示
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 添加兼容方法
    public Boolean isLargeItem() {
        return isLargeItem;
    }

    public Boolean isFragile() {
        return isFragile;
    }

    public Boolean isNeedsPackaging() {
        return needsPackaging;
    }

    // 添加标准getter形式以兼容可能的调用
    public Boolean getLargeItem() {
        return isLargeItem;
    }

    public void setLargeItem(Boolean largeItem) {
        this.isLargeItem = largeItem;
    }

    public Boolean getFragile() {
        return isFragile;
    }

    public void setFragile(Boolean fragile) {
        this.isFragile = fragile;
    }
}