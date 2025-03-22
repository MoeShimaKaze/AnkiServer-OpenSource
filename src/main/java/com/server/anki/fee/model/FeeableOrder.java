package com.server.anki.fee.model;

import com.server.anki.shopping.enums.MerchantLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 可计费订单接口
 * 定义了可以进行费用计算的订单必须具备的属性和方法
 */
public interface FeeableOrder {
    // 基本信息
    UUID getOrderNumber();           // 订单编号
    FeeType getFeeType();           // 费用类型
    LocalDateTime getCreatedTime(); // 创建时间

    // 配送信息
    Double getPickupLatitude();    // 取件纬度
    Double getPickupLongitude();   // 取件经度
    Double getDeliveryLatitude();  // 配送纬度
    Double getDeliveryLongitude(); // 配送经度
    Double getDeliveryDistance();  // 配送距离

    // 商品信息
    Double getWeight();           // 重量
    boolean isLargeItem();        // 是否大件
    BigDecimal getProductPrice(); // 商品价格（如果有）
    Integer getQuantity();        // 商品数量（如果有）
    BigDecimal getExpectedPrice();// 预期价格（代购订单）

    // 商家信息
    boolean hasMerchant();        // 是否有商家
    MerchantLevel getMerchantLevel(); // 商家等级（如果有）

    // 时间信息
    LocalDateTime getExpectedDeliveryTime(); // 预计送达时间
    LocalDateTime getDeliveredTime();        // 实际送达时间

    // 增值服务
    boolean needsInsurance();         // 是否需要保险
    BigDecimal getDeclaredValue();    // 声明价值
    boolean hasSignatureService();    // 是否需要签名服务
    boolean hasPackagingService();    // 是否需要包装服务

    // 费用分配相关
    BigDecimal getDeliveryIncome();   // 配送收入
    boolean isStandardDelivery();     // 是否标准配送
}