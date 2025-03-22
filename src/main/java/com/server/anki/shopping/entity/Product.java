// 更新 Product.java 实体类，添加审核备注字段

package com.server.anki.shopping.entity;

import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类
 * 记录商品的详细信息
 */
@Entity
@Table(name = "product")
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "market_price")
    private BigDecimal marketPrice;

    @Column(name = "cost_price")
    private BigDecimal costPrice;

    @Column(name = "wholesale_price")
    private BigDecimal wholesalePrice;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "weight", nullable = false)
    private Double weight; // 商品重量(kg)

    @Column(name = "length")
    private Double length; // 商品长度(cm)

    @Column(name = "width")
    private Double width;  // 商品宽度(cm)

    @Column(name = "height")
    private Double height; // 商品高度(cm)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ProductCategory category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "sku_code", unique = true)
    private String skuCode;

    @Column(name = "barcode")
    private String barcode;

    // 添加兼容方法
    // 修改为符合Java Bean规范的命名
    @Getter
    @Column(name = "is_large_item", nullable = false)
    private boolean largeItem;

    @Getter
    @Column(name = "needs_packaging", nullable = false)
    private boolean needsPackaging = false;

    @Getter
    @Column(name = "is_fragile", nullable = false)
    private boolean fragile = false;

    @Column(name = "max_buy_limit")
    private Integer maxBuyLimit;

    @Column(name = "min_buy_limit", nullable = false)
    private Integer minBuyLimit = 1;

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "rating", nullable = false)
    private Double rating = 5.0;

    // 添加审核备注字段
    @Column(name = "review_remark", length = 500)
    private String reviewRemark;

    // 添加审核时间和审核员ID字段
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 计算商品体积(m³)
     */
    public Double calculateVolume() {
        if (length == null || width == null || height == null) {
            return null;
        }
        return (length * width * height) / 1_000_000.0;
    }

    /**
     * 判断是否需要包装服务
     */
    public boolean needsPackaging() {
        return this.fragile ||
                this.category == ProductCategory.FOOD ||
                this.category == ProductCategory.ELECTRONICS;
    }

    public void setIsLargeItem(boolean isLargeItem) {
        this.largeItem = isLargeItem;
    }

    public void setIsFragile(boolean isFragile) {
        this.fragile = isFragile;
    }

    /**
     * 获取商品当前售价
     */
    public BigDecimal getCurrentPrice() {
        return this.price != null ? this.price : this.marketPrice;
    }

    /**
     * 更新商品评分
     */
    public void updateRating(Double newRating) {
        if (newRating != null && newRating >= 0 && newRating <= 5) {
            this.rating = (this.rating * this.salesCount + newRating) / (this.salesCount + 1);
        }
    }

    /**
     * 验证购买数量是否合法
     */
    public boolean isValidPurchaseQuantity(int quantity) {
        if (minBuyLimit != null && quantity < minBuyLimit) {
            return false;
        }
        if (maxBuyLimit != null && quantity > maxBuyLimit) {
            return false;
        }
        return quantity <= stock;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateLargeItem();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateLargeItem();
    }

    /**
     * 自动计算是否大件商品
     */
    private void calculateLargeItem() {
        Double volume = calculateVolume();
        boolean isLargeVolume = volume != null && volume > 0.027; // 大于0.027m³
        boolean isHeavy = weight != null && weight > 3.0; // 大于3kg
        this.largeItem = isLargeVolume || isHeavy;
    }
}