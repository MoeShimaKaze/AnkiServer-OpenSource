package com.server.anki.timeout.model;

import lombok.Data;

@Data
public class UserTimeoutRanking {
    private Long userId;
    private String username;
    private int pickupTimeouts;
    private int deliveryTimeouts;
    private int confirmationTimeouts;
    private int totalTimeouts;
    private int totalOrders; // 用于计算超时率
    private double timeoutRate;
}