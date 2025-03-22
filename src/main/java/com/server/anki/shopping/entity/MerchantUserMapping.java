package com.server.anki.shopping.entity;

import com.server.anki.shopping.enums.MerchantUserRole;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商家用户映射实体类
 * 管理商家与用户之间的多对多关系及权限
 */
@Entity
@Table(name = "merchant_user_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_info_id", "user_id"}))
@Getter
@Setter
public class MerchantUserMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "merchant_info_id", nullable = false)
    private MerchantInfo merchantInfo;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MerchantUserRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;

    @Column(name = "invitation_accepted")
    private Boolean invitationAccepted = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}