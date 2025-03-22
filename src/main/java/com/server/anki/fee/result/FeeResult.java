package com.server.anki.fee.result;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 费用计算结果
 * 封装了订单费用计算的完整结果
 */
@Data
@Builder
public class FeeResult {
    // 订单基本信息
    private UUID orderNumber;           // 订单编号
    private LocalDateTime calculateTime; // 计算时间

    /**
     * -- GETTER --
     *  获取配送距离
     *  如果未计算则返回null
     */
    // 配送距离信息
    @Getter
    private Double deliveryDistance;    // 配送距离(公里)
    /**
     * -- GETTER --
     *  获取配送体积
     *  如果未计算则返回null
     */
    @Getter
    private Double deliveryVolume;      // 配送体积(立方米)

    // 基础费用
    private BigDecimal baseFee;         // 基础费用
    private BigDecimal weightFee;       // 重量费用
    private BigDecimal distanceFee;     // 距离费用

    // 商品相关费用
    private BigDecimal productFee;      // 商品费用
    private BigDecimal serviceFee;      // 服务费用

    // 配送相关费用
    private BigDecimal deliveryFee;     // 配送费用
    private BigDecimal insuranceFee;    // 保险费用

    // 附加费用
    private BigDecimal timeRangeFee;    // 特殊时段费用
    private BigDecimal holidayFee;      // 节假日费用

    // 总费用
    private BigDecimal totalFee;        // 总费用

    // 费用分配方案
    private FeeDistribution distribution;  // 费用分配

    // 折扣信息
    private BigDecimal discountAmount;    // 折扣金额
    private String discountDescription;   // 折扣说明

    /**
     * 获取实际应付金额
     */
    public BigDecimal getPayableAmount() {
        return totalFee.subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);
    }

    /**
     * 检查费用计算是否合理
     */
    public boolean isValid() {
        // 验证费用不为空且大于等于0
        if (baseFee == null || baseFee.compareTo(BigDecimal.ZERO) < 0 ||
                totalFee == null || totalFee.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }

        // 验证费用分配是否合理
        if (distribution == null || !distribution.isValid()) {
            return false;
        }

        // 验证配送距离是否合理
        if (deliveryDistance != null && deliveryDistance < 0) {
            return false;
        }

        // 验证配送体积是否合理
        return deliveryVolume == null || deliveryVolume >= 0;
    }

    @Override
    public String toString() {
        return String.format("FeeResult{orderNumber=%s, distance=%.2fkm, volume=%.3fm³, totalFee=%s, payableAmount=%s}",
                orderNumber,
                deliveryDistance != null ? deliveryDistance : 0.0,
                deliveryVolume != null ? deliveryVolume : 0.0,
                totalFee,
                getPayableAmount());
    }
}