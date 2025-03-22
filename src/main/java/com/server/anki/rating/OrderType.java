package com.server.anki.rating;

import lombok.Getter;

/**
 * 订单类型枚举
 * 用于区分不同的订单来源
 */
@Getter
public enum OrderType {
    MAIL_ORDER("快递代拿"),
    SHOPPING_ORDER("商家订单"),
    PURCHASE_REQUEST("商品代购");

    private final String description;

    OrderType(String description) {
        this.description = description;
    }

}