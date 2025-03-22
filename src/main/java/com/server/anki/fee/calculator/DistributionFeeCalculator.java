package com.server.anki.fee.calculator;

import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 分配费用计算器
 * 负责计算订单费用的分配方案,包括:
 * 1. 计算配送员收入
 * 2. 计算平台收入
 * 3. 计算商家收入(商品订单)
 */
@Component
public class DistributionFeeCalculator {
    private static final Logger logger = LoggerFactory.getLogger(DistributionFeeCalculator.class);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private FeeConfiguration config;

    // 默认费率设置（可根据实际业务需求调整）
    private static final BigDecimal DEFAULT_PLATFORM_RATE = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_DELIVERY_RATE = new BigDecimal("0.80");
    private static final BigDecimal DEFAULT_MERCHANT_RATE = new BigDecimal("0.10");

    /**
     * 计算费用分配
     */
    public FeeDistribution calculateDistribution(FeeableOrder order, BigDecimal totalFee) {
        logger.debug("开始计算订单 {} 的费用分配", order.getOrderNumber());

        // 1. 计算平台收入
        BigDecimal platformIncome = calculatePlatformIncome(totalFee, order.getFeeType());

        // 2. 计算配送员收入
        BigDecimal deliveryIncome = calculateDeliveryIncome(totalFee, order.getFeeType());

        // 3. 计算商家收入(如果是商品订单)
        BigDecimal merchantIncome = calculateMerchantIncome(order, totalFee);

        // 4. 构建费用分配结果
        FeeDistribution distribution = new FeeDistribution(
                deliveryIncome,
                platformIncome,
                merchantIncome
        );

        logger.debug("订单 {} 费用分配计算完成: {}", order.getOrderNumber(), distribution);
        return distribution;
    }

    /**
     * 计算平台收入
     */
    private BigDecimal calculatePlatformIncome(BigDecimal totalFee, FeeType feeType) {
        BigDecimal platformRate = config.getPlatformRate(feeType);
        if (platformRate == null) {
            logger.warn("平台费率为null，使用默认费率 {}", DEFAULT_PLATFORM_RATE);
            platformRate = DEFAULT_PLATFORM_RATE;
        }
        return totalFee.multiply(platformRate).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算配送员收入
     */
    private BigDecimal calculateDeliveryIncome(BigDecimal totalFee, FeeType feeType) {
        BigDecimal deliveryRate = config.getDeliveryRate(feeType);
        if (deliveryRate == null) {
            logger.warn("配送费率为null，使用默认费率 {}", DEFAULT_DELIVERY_RATE);
            deliveryRate = DEFAULT_DELIVERY_RATE;
        }
        return totalFee.multiply(deliveryRate).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算商家收入
     */
    private BigDecimal calculateMerchantIncome(FeeableOrder order, BigDecimal totalFee) {
        if (!order.hasMerchant()) {
            return BigDecimal.ZERO;
        }
        BigDecimal merchantRate = config.getMerchantRate(
                order.getFeeType(),
                order.getMerchantLevel()
        );
        if (merchantRate == null) {
            logger.warn("商家费率为null，使用默认费率 {}", DEFAULT_MERCHANT_RATE);
            merchantRate = DEFAULT_MERCHANT_RATE;
        }
        return totalFee.multiply(merchantRate).setScale(SCALE, ROUNDING_MODE);
    }
}
