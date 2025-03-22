package com.server.anki.fee.model;

import lombok.Getter;

/**
 * 费用类型枚举
 * 定义了系统支持的所有费用计算类型
 */
@Getter
public enum FeeType {
    ALL_ORDERS("所有订单类型", "适用于所有类型的订单"),
    MAIL_ORDER("代拿订单", "提供校园快递代拿服务"),
    SHOPPING_ORDER("商品订单", "购买商家商品的订单"),
    PURCHASE_ORDER("代购订单", "提供商品代购服务");

    private final String label;
    private final String description;

    FeeType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * 判断是否支持配送服务
     */
    public boolean supportsDelivery() {
        if (this == ALL_ORDERS) {
            return false; // ALL_ORDERS本身不是一个实际的订单类型，所以返回false
        }
        return this == MAIL_ORDER ||
                this == SHOPPING_ORDER ||
                this == PURCHASE_ORDER;
    }

    /**
     * 判断是否涉及商品交易
     */
    public boolean involvesProduct() {
        if (this == ALL_ORDERS) {
            return false;
        }
        return this == SHOPPING_ORDER ||
                this == PURCHASE_ORDER;
    }

    /**
     * 判断是否需要商家参与
     */
    public boolean requiresMerchant() {
        if (this == ALL_ORDERS) {
            return false;
        }
        return this == SHOPPING_ORDER;
    }

    /**
     * 获取默认的服务费率
     */
    public double getDefaultServiceRate() {
        return switch (this) {
            case ALL_ORDERS -> 0.10;
            case MAIL_ORDER -> 0.10;      // 10%
            case SHOPPING_ORDER -> 0.05;   // 5%
            case PURCHASE_ORDER -> 0.15;   // 15%
        };
    }

    /**
     * 获取最大配送距离（公里）
     */
    public double getMaxDeliveryDistance() {
        return switch (this) {
            case ALL_ORDERS -> 6.0;
            case MAIL_ORDER -> 5.0;
            case SHOPPING_ORDER -> 3.0;
            case PURCHASE_ORDER -> 4.0;
        };
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", name(), label);
    }
}