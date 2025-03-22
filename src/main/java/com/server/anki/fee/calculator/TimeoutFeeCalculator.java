package com.server.anki.fee.calculator;

import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.model.TimeoutType;
import com.server.anki.marketing.SpecialDateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 超时费用计算器
 * 负责计算订单的超时费用,包括:
 * 1. 计算取件超时费用
 * 2. 计算配送超时费用
 * 3. 计算其他类型超时费用
 */
@Component
public class TimeoutFeeCalculator {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutFeeCalculator.class);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private FeeConfiguration config;

    @Autowired
    private SpecialDateService specialDateService;
    /**
     * 计算超时费用
     */
    public BigDecimal calculateTimeoutFee(FeeableOrder order, TimeoutType timeoutType) {
        logger.debug("开始计算订单 {} 的超时费用, 类型: {}",
                order.getOrderNumber(), timeoutType);

        // 1. 获取基础超时费用
        BigDecimal baseFee = getBaseTimeoutFee(timeoutType, order.getFeeType());

        // 2. 获取超时开始时间
        LocalDateTime startTime = getTimeoutStartTime(order, timeoutType);

        // 3. 计算最终费用
        BigDecimal timeoutFee = calculateFinalTimeoutFee(order, baseFee, startTime);

        logger.debug("订单 {} 超时费用计算完成: {}", order.getOrderNumber(), timeoutFee);
        return timeoutFee;
    }

    /**
     * 获取基础超时费用
     */
    private BigDecimal getBaseTimeoutFee(TimeoutType timeoutType, FeeType feeType) {
        return switch (timeoutType) {
            case PICKUP -> config.getPickupTimeoutFee(feeType);
            case DELIVERY -> config.getDeliveryTimeoutFee(feeType);
            case CONFIRMATION -> config.getConfirmationTimeoutFee(feeType);
        };
    }

    /**
     * 获取超时开始时间
     */
    private LocalDateTime getTimeoutStartTime(FeeableOrder order, TimeoutType timeoutType) {
        return switch (timeoutType) {
            case PICKUP -> order.getCreatedTime();
            case DELIVERY -> order.getExpectedDeliveryTime();
            case CONFIRMATION -> order.getDeliveredTime();
        };
    }

    /**
     * 计算最终超时费用
     */
    private BigDecimal calculateFinalTimeoutFee(
            FeeableOrder order,
            BigDecimal baseFee,
            LocalDateTime startTime) {

        // 1. 应用物品特征费率
        BigDecimal adjustedFee = applyItemCharacteristics(baseFee, order);

        // 2. 应用时间累计费率
        adjustedFee = applyTimeMultiplier(adjustedFee, startTime);

        // 3. 应用特殊规则费率
        adjustedFee = applySpecialRules(adjustedFee, order);

        // 4. 限制最大扣款
        return limitMaxDeduction(adjustedFee, order);
    }

    /**
     * 应用物品特征费率
     */
    private BigDecimal applyItemCharacteristics(BigDecimal fee, FeeableOrder order) {
        BigDecimal adjustedFee = fee;

        // 大件商品加收
        if (order.isLargeItem()) {
            BigDecimal multiplier = config.getLargeItemTimeoutMultiplier(order.getFeeType());
            adjustedFee = adjustedFee.multiply(multiplier);
        }

        // 重量附加费
        if (order.getWeight() > 1.0) {
            BigDecimal weightMultiplier = calculateWeightMultiplier(order);
            adjustedFee = adjustedFee.multiply(weightMultiplier);
        }

        return adjustedFee.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算重量倍率
     */
    private BigDecimal calculateWeightMultiplier(FeeableOrder order) {
        BigDecimal extraWeight = BigDecimal.valueOf(order.getWeight() - 1);
        BigDecimal multiplier = config.getTimeoutWeightMultiplier(order.getFeeType());
        return BigDecimal.ONE.add(multiplier.multiply(extraWeight));
    }

    /**
     * 应用时间累计费率
     */
    private BigDecimal applyTimeMultiplier(BigDecimal fee, LocalDateTime startTime) {
        if (startTime == null) {
            return fee;
        }

        Duration overtime = Duration.between(startTime, LocalDateTime.now());
        long hours = overtime.toHours();

        if (hours <= 0) {
            return fee;
        }

        // 限制最大累计小时数
        hours = Math.min(hours, config.getMaxHourlyIncrements());

        // 计算时间累计倍率
        BigDecimal incrementFactor = BigDecimal.ONE.add(
                BigDecimal.valueOf(config.getHourlyIncrementRate() * hours)
        );

        return fee.multiply(incrementFactor).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 应用特殊规则费率
     */
    private BigDecimal applySpecialRules(BigDecimal fee, FeeableOrder order) {
        BigDecimal adjustedFee = fee;

        // 节假日费率
        if (config.isHoliday(order.getCreatedTime().toLocalDate())) {
            adjustedFee = adjustedFee.multiply(config.getHolidayMultiplier());
        }

        // 特殊时段费率
        BigDecimal timeMultiplier = specialDateService.getTimeRangeRateMultiplier(
                order.getCreatedTime().getHour(),
                order.getFeeType()
        );
        if (timeMultiplier.compareTo(BigDecimal.ONE) > 0) {
            adjustedFee = adjustedFee.multiply(timeMultiplier);
        }

        return adjustedFee.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 限制最大扣款金额
     */
    private BigDecimal limitMaxDeduction(BigDecimal fee, FeeableOrder order) {
        // 如果是标准配送服务,限制最大扣款不超过用户收入的80%
        if (order.isStandardDelivery()) {
            BigDecimal maxDeduction = order.getDeliveryIncome()
                    .multiply(BigDecimal.valueOf(0.8));
            return fee.min(maxDeduction);
        }
        return fee;
    }

    /**
     * 估算超时费用
     */
    public BigDecimal estimateTimeoutFee(FeeableOrder order, TimeoutType timeoutType) {
        BigDecimal baseFee = getBaseTimeoutFee(timeoutType, order.getFeeType());
        return applyItemCharacteristics(baseFee, order);
    }

    /**
     * 获取超时时限(分钟)
     */
    public long getTimeoutMinutes(FeeableOrder order, TimeoutType timeoutType) {
        return switch (timeoutType) {
            case PICKUP -> config.getPickupTimeout(order.getFeeType());
            case DELIVERY -> config.getDeliveryTimeout(order.getFeeType());
            case CONFIRMATION -> config.getConfirmationTimeout(order.getFeeType());
        };
    }
}