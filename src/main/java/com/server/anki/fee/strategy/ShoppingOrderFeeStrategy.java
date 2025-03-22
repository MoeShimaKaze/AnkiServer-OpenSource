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
 * 商品订单费用计算策略
 * 实现了商品订单的完整计费流程，包括基础费用、配送费用和服务费计算
 */
@Component
public class ShoppingOrderFeeStrategy implements FeeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ShoppingOrderFeeStrategy.class);

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
        logger.info("开始计算商品订单 {} 的费用", order.getOrderNumber());

        try {
            // 1. 计算商品总价
            BigDecimal productFee = order.getProductPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            logger.debug("订单 {} 商品总价: {}", order.getOrderNumber(), productFee);

            // 2. 计算基础费用 (考虑商品重量、体积等物理属性)
            BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
            logger.debug("订单 {} 基础费用: {}", order.getOrderNumber(), baseFee);

            // 3. 计算配送费用
            BigDecimal deliveryFee = deliveryFeeCalculator.calculateDeliveryFee(order);
            logger.debug("订单 {} 配送费用: {}", order.getOrderNumber(), deliveryFee);

            // 4. 计算服务费用
            BigDecimal serviceFee = serviceFeeCalculator.calculateServiceFee(order, baseFee);
            logger.debug("订单 {} 服务费用: {}", order.getOrderNumber(), serviceFee);

            // 5. 计算总费用
            BigDecimal totalFee = productFee.add(deliveryFee).add(serviceFee);

            // 6. 计算费用分配
            FeeDistribution distribution = distributionFeeCalculator.calculateDistribution(
                    order, totalFee);

            // 7. 构建计费结果
            FeeResult result = FeeResult.builder()
                    .orderNumber(order.getOrderNumber())
                    .productFee(productFee)
                    .baseFee(baseFee)
                    .deliveryFee(deliveryFee)
                    .serviceFee(serviceFee)
                    .totalFee(totalFee)
                    .distribution(distribution)
                    .build();

            logger.info("商品订单 {} 费用计算完成: {}", order.getOrderNumber(), result);
            return result;

        } catch (Exception e) {
            logger.error("商品订单 {} 费用计算失败: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new FeeCalculationException("商品订单费用计算失败", e);
        }
    }

    @Override
    public boolean validateFee(FeeableOrder order) {
        // 1. 验证起送金额
        if (order.getProductPrice().compareTo(getMinOrderAmount()) < 0) {
            return false;
        }

        // 2. 验证配送范围 - 增加null检查
        Double deliveryDistance = order.getDeliveryDistance();
        if (deliveryDistance != null && deliveryDistance > getMaxDeliveryDistance()) {
            return false;
        }

        // 3. 验证商品总重量
        return !(order.getWeight() > getMaxWeight());
    }

    @Override
    public FeeResult estimateFee(FeeableOrder order) {
        BigDecimal productFee = order.getProductPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
        BigDecimal deliveryFee = deliveryFeeCalculator.calculateDeliveryFee(order);
        BigDecimal serviceFee = serviceFeeCalculator.calculateServiceFee(order, baseFee);

        return FeeResult.builder()
                .orderNumber(order.getOrderNumber())
                .productFee(productFee)
                .baseFee(baseFee)
                .deliveryFee(deliveryFee)
                .serviceFee(serviceFee)
                .totalFee(productFee.add(deliveryFee).add(serviceFee))
                .build();
    }

    @Override
    public FeeType getSupportedFeeType() {
        return FeeType.SHOPPING_ORDER;
    }

    private BigDecimal getMinOrderAmount() {
        return BigDecimal.valueOf(20); // 20元起送
    }

    private double getMaxDeliveryDistance() {
        return 3.0; // 3公里
    }

    private double getMaxWeight() {
        return 10.0; // 10公斤
    }
}