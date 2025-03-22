package com.server.anki.shopping.controller.response;

import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private Long storeId;
    private String storeName;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal costPrice;
    private BigDecimal marketPrice;
    private BigDecimal wholesalePrice;
    private Integer stock;
    private ProductStatus status;
    private ProductCategory category;
    private String imageUrl;
    private String barcode;
    private String skuCode;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private Boolean isLargeItem;
    private Boolean isFragile;
    private Boolean needsPackaging;
    private Integer maxBuyLimit;
    private Integer minBuyLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer salesCount;
    private Double rating;
    private Integer viewCount;
    private Boolean canPurchase;

    // 添加审核相关字段
    private String reviewRemark;
    private LocalDateTime reviewedAt;
    private Long reviewerId;

    /**
     * 将Product实体转换为响应对象
     */
    public static ProductResponse fromProduct(Product product) {
        if (product == null) return null;

        Boolean canPurchase = product.getStatus() == ProductStatus.ON_SALE &&
                product.getStock() > 0 &&
                product.getStore() != null &&
                product.getStore().getStatus() == com.server.anki.shopping.enums.StoreStatus.ACTIVE;

        return ProductResponse.builder()
                .id(product.getId())
                .storeId(product.getStore().getId())
                .storeName(product.getStore().getStoreName())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .marketPrice(product.getMarketPrice())
                .wholesalePrice(product.getWholesalePrice())
                .stock(product.getStock())
                .status(product.getStatus())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .barcode(product.getBarcode())
                .skuCode(product.getSkuCode())
                .weight(product.getWeight())
                .length(product.getLength())
                .width(product.getWidth())
                .height(product.getHeight())
                // 修正以下三个布尔属性的getter方法调用
                .isLargeItem(product.isLargeItem())
                .isFragile(product.isFragile())
                .needsPackaging(product.isNeedsPackaging()) // 或使用needsPackaging()方法
                .maxBuyLimit(product.getMaxBuyLimit())
                .minBuyLimit(product.getMinBuyLimit())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .salesCount(product.getSalesCount())
                .rating(product.getRating())
                .viewCount(product.getViewCount())
                .canPurchase(canPurchase)
                // 添加审核相关字段
                .reviewRemark(product.getReviewRemark())
                .reviewedAt(product.getReviewedAt())
                .reviewerId(product.getReviewerId())
                .build();
    }
}