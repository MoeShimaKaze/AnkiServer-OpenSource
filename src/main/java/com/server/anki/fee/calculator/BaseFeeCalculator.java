package com.server.anki.fee.calculator;

import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 基础费用计算器
 * 负责计算订单的基础费用,包括:
 * 1. 根据重量计算基础运费
 * 2. 计算重物附加费
 * 3. 计算距离附加费
 */
@Component
public class BaseFeeCalculator {
    private static final Logger logger = LoggerFactory.getLogger(BaseFeeCalculator.class);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private FeeConfiguration config;

    /**
     * 计算基础费用
     * @param order 订单信息
     * @return 基础费用
     */
    public BigDecimal calculateBaseFee(FeeableOrder order) {
        try {
            // 1. 计算重量费用
            BigDecimal weightFee = calculateWeightFee(order);

            // 2. 应用大件商品倍率
            if (order.isLargeItem()) {
                weightFee = applyLargeItemMultiplier(weightFee, order.getFeeType());
            }

            // 3. 应用重量倍率
            if (order.getWeight() > 1.0) {
                weightFee = applyWeightMultiplier(weightFee, order.getWeight(), order.getFeeType());
            }

            return weightFee;
        } catch (Exception e) {
            logger.error("计算基础费用时发生错误: {}", e.getMessage(), e);
            // 返回一个默认的基础费用，避免整个流程中断
            return BigDecimal.valueOf(10);
        }
    }

    /**
     * 计算重量费用
     */
    private BigDecimal calculateWeightFee(FeeableOrder order) {
        return BigDecimal.valueOf(Math.ceil(order.getWeight() / 0.5))
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 应用大件商品倍率
     */
    private BigDecimal applyLargeItemMultiplier(BigDecimal fee, FeeType feeType) {
        BigDecimal multiplier = config.getLargeItemMultiplier(feeType);
        return fee.multiply(multiplier).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 应用重量倍率
     */
    private BigDecimal applyWeightMultiplier(BigDecimal fee, double weight, FeeType feeType) {
        try {
            BigDecimal extraWeight = BigDecimal.valueOf(weight - 1);
            BigDecimal multiplier = BigDecimal.ONE.add(
                    config.getWeightMultiplier(feeType).multiply(extraWeight)
            );
            return fee.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            logger.error("应用重量倍率时发生错误: {}", e.getMessage(), e);
            // 返回原费用
            return fee;
        }
    }
}