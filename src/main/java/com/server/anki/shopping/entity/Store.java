// Store.java
package com.server.anki.shopping.entity;

import com.server.anki.shopping.enums.StoreStatus;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 店铺实体类
 * 记录商家店铺的基本信息
 */
@Entity
@Table(name = "store")
@Getter
@Setter
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;  // 店铺所属商家

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "merchant_info_id")
    private MerchantInfo merchantInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StoreStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "business_hours")
    private String businessHours;

    @Column(name = "location")
    private String location;

    @OneToMany(mappedBy = "store")
    private List<Product> products;

    // Store.java 中添加以下字段
    @Column(name = "latitude")
    private Double latitude;  // 店铺纬度

    @Column(name = "longitude")
    private Double longitude; // 店铺经度

    @Column(name = "remarks", length = 500)
    private String remarks;  // 添加备注字段，用于存储审核意见等信息

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // 初始化集合字段
        if (products == null) {
            products = new ArrayList<>();
        }
    }

    public List<Product> getProducts() {
        if (products == null) {
            products = new ArrayList<>();
        }
        return products;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}