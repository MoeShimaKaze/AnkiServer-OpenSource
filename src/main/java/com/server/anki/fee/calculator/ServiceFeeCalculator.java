package com.server.anki.fee.calculator;

import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 服务费用计算器
 * 负责计算订单的服务费用,包括:
 * 1. 计算平台服务费
 * 2. 计算保险费用
 * 3. 计算其他增值服务费用
 */
@Component
public class ServiceFeeCalculator {
    private static final Logger logger = LoggerFactory.getLogger(ServiceFeeCalculator.class);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private FeeConfiguration config;

    /**
     * 计算服务费用
     */
    public BigDecimal calculateServiceFee(FeeableOrder order, BigDecimal baseFee) {
        logger.debug("开始计算订单 {} 的服务费用", order.getOrderNumber());

        // 1. 计算基础服务费
        BigDecimal serviceFee = calculateBaseServiceFee(baseFee, order.getFeeType());

        // 2. 计算保险费用
        serviceFee = serviceFee.add(calculateInsuranceFee(order));

        // 3. 计算其他增值服务费用
        serviceFee = serviceFee.add(calculateValueAddedFee(order));

        logger.debug("订单 {} 服务费用计算完成: {}", order.getOrderNumber(), serviceFee);
        return serviceFee;
    }

    /**
     * 计算基础服务费
     */
    private BigDecimal calculateBaseServiceFee(BigDecimal baseFee, FeeType feeType) {
        BigDecimal serviceRate = config.getServiceRate(feeType);
        return baseFee.multiply(serviceRate).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算保险费用
     */
    private BigDecimal calculateInsuranceFee(FeeableOrder order) {
        if (!order.needsInsurance()) {
            return BigDecimal.ZERO;
        }
        return config.getInsuranceRate(order.getFeeType())
                .multiply(order.getDeclaredValue())
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 计算增值服务费用
     */
    private BigDecimal calculateValueAddedFee(FeeableOrder order) {
        BigDecimal valueFee = BigDecimal.ZERO;

        // 根据订单的增值服务选项计算费用
        if (order.hasSignatureService()) {
            valueFee = valueFee.add(config.getSignatureServiceFee(order.getFeeType()));
        }
        if (order.hasPackagingService()) {
            valueFee = valueFee.add(config.getPackagingServiceFee(order.getFeeType()));
        }

        return valueFee;
    }
}