package com.server.anki.shopping.entity;

import com.server.anki.shopping.enums.MerchantLevel;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 商家信息实体类
 * 记录商家的详细信息和认证资料，支持多用户绑定
 */
@Entity
@Table(name = "merchant_info")
@Getter
@Setter
public class MerchantInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 商户唯一标识
    @Column(name = "merchant_uid", unique = true, nullable = false)
    private String merchantUid;

    // 主要关联用户（创建者/所有者）
    @ManyToOne
    @JoinColumn(name = "primary_user_id", nullable = false)
    private User primaryUser;

    // 关联的所有用户（员工）
    @OneToMany(mappedBy = "merchantInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MerchantUserMapping> userMappings = new ArrayList<>();

    @Column(name = "business_license", nullable = false)
    private String businessLicense;  // 营业执照号

    @Column(name = "license_image")
    private String licenseImage;     // 营业执照图片

    @Column(name = "contact_name", nullable = false)
    private String contactName;      // 联系人姓名

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;     // 联系电话

    @Column(name = "business_address")
    private String businessAddress;  // 经营地址

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_level")
    private MerchantLevel merchantLevel;

    @Column(name = "total_sales")
    private Integer totalSales = 0;  // 总销售量

    @Column(name = "rating")
    private Double rating = 5.0;     // 商家评分

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    // 添加和移除用户映射的辅助方法
    public void addUserMapping(MerchantUserMapping mapping) {
        userMappings.add(mapping);
        mapping.setMerchantInfo(this);
    }

    public void removeUserMapping(MerchantUserMapping mapping) {
        userMappings.remove(mapping);
        mapping.setMerchantInfo(null);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        merchantLevel = MerchantLevel.BRONZE;  // 默认等级

        if (merchantUid == null) {
            merchantUid = generateMerchantUid();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 生成商户UID的辅助方法
    private String generateMerchantUid() {
        return "M" + System.currentTimeMillis() +
                String.format("%04d", new Random().nextInt(10000));
    }
}