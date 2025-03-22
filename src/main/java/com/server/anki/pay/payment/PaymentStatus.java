package com.server.anki.pay.payment;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    WAITING("待支付"),
    PAID("已支付"),
    CANCELLED("已取消"),
    TIMEOUT("已超时"),
    REFUNDED("已退款");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

}