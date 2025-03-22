package com.server.anki.pay.payment;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PaymentResponse {
    private String orderNumber;
    private String payForm;      // 保留原有字段以兼容旧代码
    private String payUrl;       // 新增字段，直接支付链接
    private BigDecimal amount;
    private LocalDateTime expireTime;

    // 原有的构造函数
    public PaymentResponse(String orderNumber, String payForm, BigDecimal amount, LocalDateTime expireTime) {
        this.orderNumber = orderNumber;
        this.payForm = payForm;
        this.amount = amount;
        this.expireTime = expireTime;
    }

    // 新增构造函数 - 同时支持payForm和payUrl
    public PaymentResponse(String orderNumber, String payForm, String payUrl, BigDecimal amount, LocalDateTime expireTime) {
        this.orderNumber = orderNumber;
        this.payForm = payForm;
        this.payUrl = payUrl;
        this.amount = amount;
        this.expireTime = expireTime;
    }
}