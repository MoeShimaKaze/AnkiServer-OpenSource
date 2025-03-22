// MerchantLevel.java
package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 商家等级枚举
 * 用于商家信用评级和权益管理
 */
@Getter
public enum MerchantLevel {
    BRONZE("铜牌商家", 1, 0.12),
    SILVER("银牌商家", 2, 0.10),
    GOLD("金牌商家", 3, 0.08),
    PLATINUM("白金商家", 4, 0.06),
    DIAMOND("钻石商家", 5, 0.05);

    private final String label;
    private final int level;
    private final double platformFeeRate; // 平台服务费率

    MerchantLevel(String label, int level, double platformFeeRate) {
        this.label = label;
        this.level = level;
        this.platformFeeRate = platformFeeRate;
    }
}