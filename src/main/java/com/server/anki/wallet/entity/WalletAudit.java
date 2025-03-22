package com.server.anki.wallet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "wallet_audit")
public class WalletAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action")
    private String action;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "additional_info")
    private String additionalInfo;

    // Getters and setters

    @Override
    public String toString() {
        return "WalletAudit{" +
                "id=" + id +
                ", walletId=" + walletId +
                ", userId=" + userId +
                ", action='" + action + '\'' +
                ", amount=" + amount +
                ", reason='" + reason + '\'' +
                ", performedBy='" + performedBy + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}