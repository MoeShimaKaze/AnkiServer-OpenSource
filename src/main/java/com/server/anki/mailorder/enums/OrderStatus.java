package com.server.anki.mailorder.enums;

public enum OrderStatus {
    MERCHANT_PENDING,     // 已支付，等待商家确认
    PAYMENT_PENDING,
    CANCELLED,
    PENDING,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    PLATFORM_INTERVENTION,
    REFUNDING,
    REFUNDED,
    LOCKED,
    PAYMENT_TIMEOUT
}
