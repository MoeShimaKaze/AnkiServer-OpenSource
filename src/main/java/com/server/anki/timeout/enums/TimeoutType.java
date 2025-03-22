package com.server.anki.timeout.enums;

import lombok.Getter;

/**
 * 超时类型枚举
 * 定义了订单处理过程中可能出现的各种超时类型
 */
@Getter
public enum TimeoutType {
    /**
     * 取件超时
     * 快递员未在规定时间内取件
     */
    PICKUP("PICKUP", "取件超时", "快递员未在规定时间内完成取件"),

    /**
     * 配送超时
     * 快递员未在规定时间内完成配送
     */
    DELIVERY("DELIVERY", "配送超时", "快递员未在规定时间内完成配送"),

    /**
     * 确认超时
     * 用户未在规定时间内确认收货
     */
    CONFIRMATION("CONFIRMATION", "确认超时", "用户未在规定时间内确认收货"),

    /**
     * 系统干预超时
     * 需要平台介入处理的超时情况
     */
    INTERVENTION("INTERVENTION", "系统干预", "需要平台介入处理的超时情况");

    private final String code;
    private final String name;
    private final String description;

    TimeoutType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    /**
     * 根据代码获取超时类型
     *
     * @param code 类型代码
     * @return 对应的超时类型，如果未找到返回null
     */
    public static TimeoutType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        for (TimeoutType type : TimeoutType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查是否需要扣费的超时类型
     */
    public boolean requiresFee() {
        return this == PICKUP || this == DELIVERY;
    }

    /**
     * 检查是否需要系统干预
     */
    public boolean requiresIntervention() {
        return this == INTERVENTION;
    }

    /**
     * 获取对应的警告类型
     */
    public TimeoutType getWarningType() {
        return this;
    }

    /**
     * 判断是否为配送相关的超时
     */
    public boolean isDeliveryRelated() {
        return this == PICKUP || this == DELIVERY;
    }

    /**
     * 获取用户友好的消息描述
     */
    public String getUserFriendlyMessage() {
        return switch (this) {
            case PICKUP -> "快递员未及时取件，系统已自动处理";
            case DELIVERY -> "快递员未在承诺时间内送达，已进入异常处理";
            case CONFIRMATION -> "请尽快确认收货，避免订单自动完成";
            case INTERVENTION -> "订单已进入平台处理流程，请耐心等待";
        };
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", name, code);
    }
}