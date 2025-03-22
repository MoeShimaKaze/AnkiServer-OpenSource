// DeliveryType.java
package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 配送类型枚举
 * 用于定义不同的配送方式及其服务费率
 */
@Getter
public enum DeliveryType {
    MUTUAL("互助配送", 0.10),      // 互助配送：配送费的10%作为服务费
    PLATFORM("平台配送", 0.35);    // 平台配送：配送费的35%作为服务费

    private final String label;
    private final double serviceFeeRate;

    DeliveryType(String label, double serviceFeeRate) {
        this.label = label;
        this.serviceFeeRate = serviceFeeRate;
    }
}