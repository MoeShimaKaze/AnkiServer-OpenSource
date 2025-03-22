package com.server.anki.fee.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用分配结果
 * 定义了订单费用在各方之间的分配方案
 */
@Data
@AllArgsConstructor
public class FeeDistribution {
    private BigDecimal deliveryIncome;    // 配送员收入
    private BigDecimal platformIncome;     // 平台收入
    private BigDecimal merchantIncome;     // 商家收入（如果有）

    /**
     * 验证费用分配是否合理
     */
    public boolean isValid() {
        // 验证各项收入非空且大于等于0
        if (deliveryIncome == null || platformIncome == null) {
            return false;
        }
        if (deliveryIncome.compareTo(BigDecimal.ZERO) < 0 ||
                platformIncome.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        // 商家收入可以为null（非商品订单）
        return merchantIncome == null || merchantIncome.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 获取总分配金额
     */
    public BigDecimal getTotalDistribution() {
        BigDecimal total = deliveryIncome.add(platformIncome);
        if (merchantIncome != null) {
            total = total.add(merchantIncome);
        }
        return total;
    }

    @Override
    public String toString() {
        return String.format("FeeDistribution{delivery=%s, platform=%s, merchant=%s}",
                deliveryIncome, platformIncome, merchantIncome);
    }
}