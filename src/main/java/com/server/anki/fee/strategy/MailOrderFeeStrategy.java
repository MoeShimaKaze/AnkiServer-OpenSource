package com.server.anki.fee.strategy;

import com.server.anki.fee.calculator.BaseFeeCalculator;
import com.server.anki.fee.calculator.DeliveryFeeCalculator;
import com.server.anki.fee.calculator.DistributionFeeCalculator;
import com.server.anki.fee.calculator.ServiceFeeCalculator;
import com.server.anki.fee.exception.FeeCalculationException;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeDistribution;
import com.server.anki.fee.result.FeeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 代拿订单费用计算策略
 * 实现了代拿订单的完整计费流程
 */
@Component
public class MailOrderFeeStrategy implements FeeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(MailOrderFeeStrategy.class);

    @Autowired
    private BaseFeeCalculator baseFeeCalculator;

    @Autowired
    private DeliveryFeeCalculator deliveryFeeCalculator;

    @Autowired
    private ServiceFeeCalculator serviceFeeCalculator;

    @Autowired
    private DistributionFeeCalculator distributionFeeCalculator;

    @Override
    public FeeResult calculateFee(FeeableOrder order) {
        logger.info("开始计算代拿订单 {} 的费用", order.getOrderNumber());

        try {
            // 1. 计算基础费用
            BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
            logger.debug("订单 {} 基础费用: {}", order.getOrderNumber(), baseFee);

            // 2. 计算配送费用
            BigDecimal deliveryFee = deliveryFeeCalculator.calculateDeliveryFee(order);
            logger.debug("订单 {} 配送费用: {}", order.getOrderNumber(), deliveryFee);

            // 3. 计算服务费用
            BigDecimal serviceFee = serviceFeeCalculator.calculateServiceFee(order, baseFee);
            logger.debug("订单 {} 服务费用: {}", order.getOrderNumber(), serviceFee);

            // 4. 计算总费用
            BigDecimal totalFee = baseFee.add(deliveryFee).add(serviceFee);

            // 5. 计算费用分配
            FeeDistribution distribution = distributionFeeCalculator.calculateDistribution(
                    order, totalFee);

            // 6. 构建计费结果
            FeeResult result = FeeResult.builder()
                    .orderNumber(order.getOrderNumber())
                    .baseFee(baseFee)
                    .deliveryFee(deliveryFee)
                    .serviceFee(serviceFee)
                    .totalFee(totalFee)
                    .distribution(distribution)
                    .build();

            logger.info("代拿订单 {} 费用计算完成: {}", order.getOrderNumber(), result);
            return result;

        } catch (Exception e) {
            logger.error("代拿订单 {} 费用计算失败: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new FeeCalculationException("代拿订单费用计算失败", e);
        }
    }

    @Override
    public boolean validateFee(FeeableOrder order) {
        // 1. 验证配送距离
        if (order.getDeliveryDistance() > getMaxDeliveryDistance()) {
            return false;
        }

        // 2. 验证订单金额
        if (order.getDeclaredValue().compareTo(getMaxOrderValue()) > 0) {
            return false;
        }

        // 3. 验证重量限制
        return !(order.getWeight() > getMaxWeight());
    }

    @Override
    public FeeResult estimateFee(FeeableOrder order) {
        // 简化计算流程,仅计算基础费用
        BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
        BigDecimal estimatedTotal = baseFee.multiply(BigDecimal.valueOf(1.5));

        return FeeResult.builder()
                .orderNumber(order.getOrderNumber())
                .baseFee(baseFee)
                .totalFee(estimatedTotal)
                .build();
    }

    @Override
    public FeeType getSupportedFeeType() {
        return FeeType.MAIL_ORDER;
    }

    private double getMaxDeliveryDistance() {
        return 5.0; // 5公里
    }

    private BigDecimal getMaxOrderValue() {
        return BigDecimal.valueOf(5000); // 5000元
    }

    private double getMaxWeight() {
        return 20.0; // 20公斤
    }
}