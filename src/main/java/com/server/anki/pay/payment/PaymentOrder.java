package com.server.anki.pay.payment;

import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 * 存储与各类订单相关的支付信息
 */
@Entity
@Table(name = "payment_orders")
@Data
@NoArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 支付订单号
     */
    @Column(nullable = false, unique = true)
    private String orderNumber;

    /**
     * 订单类型
     * 用于区分不同类型的订单
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    /**
     * 订单信息
     * 统一存储所有类型的订单标识信息
     */
    @Column(name = "order_info", nullable = false)
    private String orderInfo;

    /**
     * 用户
     */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * 支付金额
     */
    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * 支付状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    private LocalDateTime updatedTime;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 支付宝交易号
     */
    private String alipayTradeNo;
}