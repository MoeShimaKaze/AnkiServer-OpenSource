package com.server.anki.pay.payment;

/**
 * 订单类型枚举
 * 用于标识不同类型的订单
 */
public enum OrderType {
    /** 邮件订单 */
    MAIL_ORDER,

    /** 商品订单 */
    SHOPPING_ORDER,

    /** 代购需求订单 */
    PURCHASE_REQUEST,

    /** 钱包充值 */
    WALLET_RECHARGE,

    /** 其他订单类型 */
    OTHER;

    /**
     * 从字符串解析订单类型
     * @param typeStr 类型字符串
     * @return 订单类型，如果无法识别则返回OTHER
     */
    public static OrderType fromString(String typeStr) {
        if (typeStr == null) {
            return OTHER;
        }

        try {
            return OrderType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}