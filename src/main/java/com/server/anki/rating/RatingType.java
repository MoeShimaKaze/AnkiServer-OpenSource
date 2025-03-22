package com.server.anki.rating;

import lombok.Getter;

/**
 * 评价类型枚举
 * 定义了不同角色之间的评价关系
 */
@Getter
public enum RatingType {
    // 快递代拿评价类型
    SENDER_TO_DELIVERER("寄件人评价快递员"),
    DELIVERER_TO_SENDER("快递员评价寄件人"),
    SENDER_TO_PLATFORM("寄件人评价平台"),

    // 商家订单评价类型
    CUSTOMER_TO_MERCHANT("顾客评价商家"),
    MERCHANT_TO_CUSTOMER("商家评价顾客"),
    CUSTOMER_TO_DELIVERER("顾客评价配送员"),
    DELIVERER_TO_CUSTOMER("配送员评价顾客"),

    // 商品代购评价类型
    REQUESTER_TO_FULFILLER("需求方评价代购方"),
    FULFILLER_TO_REQUESTER("代购方评价需求方");

    private final String description;

    RatingType(String description) {
        this.description = description;
    }

}