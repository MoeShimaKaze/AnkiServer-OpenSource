package com.server.anki.wallet.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    @JsonBackReference
    private User user;

    @Column(name = "balance", precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "pending_balance", precision = 10, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "pending_balance_release_time")
    private LocalDateTime pendingBalanceReleaseTime;

    public Wallet() {
        this.balance = BigDecimal.ZERO;
        this.pendingBalance = BigDecimal.ZERO;
    }

    // Getters and setters

    public void addPendingBalance(BigDecimal amount, LocalDateTime releaseTime) {
        this.pendingBalance = this.pendingBalance.add(amount);
        this.pendingBalanceReleaseTime = releaseTime;
    }

    public void releasePendingBalance() {
        this.balance = this.balance.add(this.pendingBalance);
        this.pendingBalance = BigDecimal.ZERO;
        this.pendingBalanceReleaseTime = null;
    }
}