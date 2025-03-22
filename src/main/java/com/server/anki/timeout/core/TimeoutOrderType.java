package com.server.anki.timeout.core;

import lombok.Getter;

/**
 * 超时订单类型枚举
 * 区分不同的订单来源
 */
@Getter
public enum TimeoutOrderType {
    /**
     * 快递代拿订单
     */
    MAIL_ORDER("快递代拿订单", "快递"),

    /**
     * 商家订单
     */
    SHOPPING_ORDER("商家订单", "商品"),

    /**
     * 代购订单
     */
    PURCHASE_REQUEST("代购订单", "代购");

    private final String description;
    private final String shortName;

    TimeoutOrderType(String description, String shortName) {
        this.description = description;
        this.shortName = shortName;
    }

    /**
     * 根据订单类型获取对应的超时处理策略
     * @return 超时处理优先级，值越大优先级越高
     */
    public int getPriority() {
        return switch (this) {
            case MAIL_ORDER -> 3;     // 快递代拿最高优先级
            case SHOPPING_ORDER -> 2; // 商家订单次优先级
            case PURCHASE_REQUEST -> 1; // 代购订单最低优先级
        };
    }

    /**
     * 获取每种订单类型的默认超时时间（分钟）
     * @return 默认超时分钟数
     */
    public int getDefaultTimeoutMinutes() {
        return switch (this) {
            case MAIL_ORDER -> 60;    // 快递代拿默认60分钟
            case SHOPPING_ORDER -> 90;  // 商家订单默认90分钟
            case PURCHASE_REQUEST -> 120; // 代购订单默认120分钟
        };
    }

    /**
     * 获取警告阈值比例
     * 例如0.8表示当达到80%超时时间时发出警告
     * @return 警告阈值比例
     */
    public double getWarningThreshold() {
        return switch (this) {
            case MAIL_ORDER -> 0.8;    // 快递代拿80%时间时警告
            case SHOPPING_ORDER -> 0.7;  // 商家订单70%时间时警告
            case PURCHASE_REQUEST -> 0.75; // 代购订单75%时间时警告
        };
    }

    /**
     * 获取归档阈值（超时次数）
     * @return 归档阈值
     */
    public int getArchiveThreshold() {
        return switch (this) {
            case MAIL_ORDER -> 3;     // 快递代拿超时3次归档
            case SHOPPING_ORDER -> 4;  // 商家订单超时4次归档
            case PURCHASE_REQUEST -> 5; // 代购订单超时5次归档
        };
    }
}