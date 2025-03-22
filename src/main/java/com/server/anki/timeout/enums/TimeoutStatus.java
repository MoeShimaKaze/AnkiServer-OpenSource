package com.server.anki.timeout.enums;

import lombok.Getter;

/**
 * 超时状态枚举
 * 定义了订单在超时处理过程中的各种状态
 */
public enum TimeoutStatus {
    /**
     * 正常状态
     * 订单处理时间在正常范围内
     */
    NORMAL("NORMAL", "正常", "订单处理时间在正常范围内", false, false),

    /**
     * 取件超时预警
     * 接近取件超时时间，需要提醒快递员
     */
    PICKUP_TIMEOUT_WARNING("PICKUP_WARNING", "取件超时预警",
            "即将超出取件时限，请尽快处理", true, false),

    /**
     * 配送超时预警
     * 接近配送超时时间，需要提醒快递员
     */
    DELIVERY_TIMEOUT_WARNING("DELIVERY_WARNING", "配送超时预警",
            "即将超出配送时限，请加快配送", true, false),

    /**
     * 确认超时预警
     * 接近确认超时时间，需要提醒用户
     */
    CONFIRMATION_TIMEOUT_WARNING("CONFIRMATION_WARNING", "确认超时预警",
            "请尽快确认收货，避免自动完成", true, false),

    /**
     * 取件超时
     * 已超出取件时间限制，需要系统处理
     */
    PICKUP_TIMEOUT("PICKUP_TIMEOUT", "取件超时",
            "已超出取件时限，系统自动处理", false, true),

    /**
     * 配送超时
     * 已超出配送时间限制，需要系统处理
     */
    DELIVERY_TIMEOUT("DELIVERY_TIMEOUT", "配送超时",
            "已超出配送时限，转入异常处理", false, true),

    /**
     * 确认超时
     * 已超出确认时间限制，系统自动完成
     */
    CONFIRMATION_TIMEOUT("CONFIRMATION_TIMEOUT", "确认超时",
            "已超出确认时限，系统自动完成", false, true);
    @Getter
    private final String code;
    @Getter
    private final String name;
    @Getter
    private final String message;
    private final boolean isWarning;
    private final boolean isTimeout;

    TimeoutStatus(String code, String name, String message,
                  boolean isWarning, boolean isTimeout) {
        this.code = code;
        this.name = name;
        this.message = message;
        this.isWarning = isWarning;
        this.isTimeout = isTimeout;
    }



    public boolean isWarning() {
        return isWarning;
    }

    public boolean isTimeout() {
        return isTimeout;
    }

    /**
     * 根据代码获取状态
     */
    public static TimeoutStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return NORMAL;
        }

        for (TimeoutStatus status : TimeoutStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return NORMAL;
    }

    /**
     * 获取对应的超时类型
     */
    public TimeoutType getTimeoutType() {
        return switch (this) {
            case PICKUP_TIMEOUT, PICKUP_TIMEOUT_WARNING -> TimeoutType.PICKUP;
            case DELIVERY_TIMEOUT, DELIVERY_TIMEOUT_WARNING -> TimeoutType.DELIVERY;
            case CONFIRMATION_TIMEOUT, CONFIRMATION_TIMEOUT_WARNING -> TimeoutType.CONFIRMATION;
            default -> null;
        };
    }

    /**
     * 判断是否需要发送通知
     */
    public boolean requiresNotification() {
        return isWarning || isTimeout;
    }

    /**
     * 获取通知级别
     */
    public NotificationLevel getNotificationLevel() {
        if (isTimeout) {
            return NotificationLevel.HIGH;
        } else if (isWarning) {
            return NotificationLevel.MEDIUM;
        } else {
            return NotificationLevel.LOW;
        }
    }

    /**
     * 获取对应的处理建议
     */
    public String getHandlingSuggestion() {
        return switch (this) {
            case PICKUP_TIMEOUT_WARNING -> "建议快递员立即前往取件点";
            case DELIVERY_TIMEOUT_WARNING -> "建议快递员优化配送路线";
            case CONFIRMATION_TIMEOUT_WARNING -> "建议系统发送提醒短信";
            case PICKUP_TIMEOUT -> "建议重新分配订单";
            case DELIVERY_TIMEOUT -> "建议平台介入处理";
            case CONFIRMATION_TIMEOUT -> "建议自动完成订单";
            default -> "无需特殊处理";
        };
    }

    /**
     * 判断是否为严重状态
     */
    public boolean isCritical() {
        return this == PICKUP_TIMEOUT || this == DELIVERY_TIMEOUT;
    }

    /**
     * 获取状态描述
     */
    @Override
    public String toString() {
        return String.format("%s[%s]-%s", name, code, isWarning ? "预警" : "超时");
    }
}
