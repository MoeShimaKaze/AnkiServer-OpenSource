package com.server.anki.fee.core;

import java.math.BigDecimal;

/**
 * 费用计算常量定义
 * 定义了费用计算过程中使用的各种常量值
 */
public final class FeeConstants {
    private FeeConstants() {
        // 私有构造函数防止实例化
    }

    // 计算精度相关常量
    public static final int FEE_SCALE = 2;  // 金额计算精度
    public static final int RATE_SCALE = 4; // 费率计算精度

    // 基础费用相关常量
    public static final double WEIGHT_UNIT = 0.5;      // 重量计费单位(kg)
    public static final double BASE_FREE_DISTANCE = 3.0; // 基础免费配送距离(km)

    // 费率限制常量
    public static final BigDecimal MIN_FEE = BigDecimal.valueOf(1);    // 最小收费
    public static final BigDecimal MAX_SERVICE_RATE = BigDecimal.valueOf(0.5); // 最大服务费率
    public static final BigDecimal MIN_SERVICE_RATE = BigDecimal.valueOf(0.1); // 最小服务费率

    // 时间相关常量
    public static final int MAX_HOURLY_INCREMENTS = 12;  // 最大累计小时数
    public static final int RUSH_HOUR_START = 17;        // 高峰开始时间
    public static final int RUSH_HOUR_END = 20;          // 高峰结束时间

    // 阈值常量
    public static final double MAX_WEIGHT = 50.0;   // 最大重量限制(kg)
    public static final double MAX_DISTANCE = 10.0; // 最大配送距离(km)
    public static final BigDecimal MAX_ORDER_VALUE = BigDecimal.valueOf(5000); // 最大订单金额

    // 默认费率常量
    public static final BigDecimal DEFAULT_PLATFORM_RATE = BigDecimal.valueOf(0.1);  // 默认平台费率
    public static final BigDecimal DEFAULT_DELIVERY_RATE = BigDecimal.valueOf(0.8);  // 默认配送费率
    public static final BigDecimal DEFAULT_SERVICE_RATE = BigDecimal.valueOf(0.1);   // 默认服务费率

    // 特殊时段费率系数
    public static final BigDecimal HOLIDAY_MULTIPLIER = BigDecimal.valueOf(1.5);  // 节假日倍率
    public static final BigDecimal NIGHT_MULTIPLIER = BigDecimal.valueOf(1.3);    // 夜间倍率
    public static final BigDecimal RUSH_HOUR_MULTIPLIER = BigDecimal.valueOf(1.2);// 高峰倍率

    // 费用分配相关常量
    public static final BigDecimal MIN_MERCHANT_RATE = BigDecimal.valueOf(0.7);  // 最低商家分成
    public static final BigDecimal MAX_PLATFORM_RATE = BigDecimal.valueOf(0.3);  // 最高平台分成

    // 超时相关常量
    public static final int TIMEOUT_WARNING_MINUTES = 10;  // 超时预警时间(分钟)
    public static final int MAX_TIMEOUT_COUNT = 3;       // 最大超时次数
    public static final BigDecimal TIMEOUT_PENALTY_RATE = BigDecimal.valueOf(0.5); // 超时罚金率
}