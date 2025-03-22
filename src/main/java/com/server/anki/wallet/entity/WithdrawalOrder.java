package com.server.anki.wallet.entity;

import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "withdrawal_order")
public class WithdrawalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "withdrawal_method", nullable = false)
    private String withdrawalMethod;

    @Column(name = "account_info", nullable = false)
    private String accountInfo;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "processed_time")
    private LocalDateTime processedTime;

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "alipay_order_id")
    private String alipayOrderId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public enum WithdrawalStatus {
        PROCESSING,  // 处理中
        SUCCESS,     // 成功
        FAILED       // 失败
    }

    // 默认构造函数
    public WithdrawalOrder() {
        this.createdTime = LocalDateTime.now();
        this.status = WithdrawalStatus.PROCESSING;
    }
}