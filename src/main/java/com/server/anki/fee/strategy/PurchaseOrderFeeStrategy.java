package com.server.anki.fee.strategy;

import com.server.anki.fee.calculator.BaseFeeCalculator;
import com.server.anki.fee.calculator.DeliveryFeeCalculator;
import com.server.anki.fee.calculator.DistributionFeeCalculator;
import com.server.anki.fee.exception.FeeCalculationException;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeDistribution;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.enums.ProductCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 代购订单费用计算策略
 * 实现了代购订单的完整计费流程
 */
@Component
public class PurchaseOrderFeeStrategy implements FeeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderFeeStrategy.class);

    @Autowired
    private DeliveryFeeCalculator deliveryFeeCalculator;

    @Autowired
    private DistributionFeeCalculator distributionFeeCalculator;

    @Autowired
    private BaseFeeCalculator baseFeeCalculator;

    @Override
    public FeeResult calculateFee(FeeableOrder order) {
        logger.info("开始计算代购订单 {} 的费用", order.getOrderNumber());

        try {
            // 1. 计算代购商品费用
            BigDecimal productFee = order.getExpectedPrice();
            logger.debug("订单 {} 商品费用: {}", order.getOrderNumber(), productFee);

            // 2. 计算基础费用 - 新增这一步
            BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
            logger.debug("订单 {} 基础费用: {}", order.getOrderNumber(), baseFee);

            // 3. 应用商品类型特定费率
            baseFee = applyProductCategoryRate(order, baseFee);
            logger.debug("订单 {} 应用商品类型费率后的基础费用: {}", order.getOrderNumber(), baseFee);

            // 4. 计算服务费用(代购服务费)
            BigDecimal serviceFee = calculatePurchaseServiceFee(order);
            logger.debug("订单 {} 代购服务费: {}", order.getOrderNumber(), serviceFee);

            // 5. 计算配送费用
            BigDecimal deliveryFee = deliveryFeeCalculator.calculateDeliveryFee(order);
            // 将基础费用加入到配送费中
            deliveryFee = deliveryFee.add(baseFee);
            logger.debug("订单 {} 配送费用(含基础费): {}", order.getOrderNumber(), deliveryFee);

            // 6. 计算总费用
            BigDecimal totalFee = productFee.add(serviceFee).add(deliveryFee);

            // 7. 计算费用分配
            FeeDistribution distribution = distributionFeeCalculator.calculateDistribution(
                    order, totalFee);

            // 8. 构建计费结果
            FeeResult result = FeeResult.builder()
                    .orderNumber(order.getOrderNumber())
                    .productFee(productFee)
                    .baseFee(baseFee)  // 添加基础费用
                    .serviceFee(serviceFee)
                    .deliveryFee(deliveryFee)
                    .totalFee(totalFee)
                    .distribution(distribution)
                    .build();

            logger.info("代购订单 {} 费用计算完成: {}", order.getOrderNumber(), result);
            return result;

        } catch (Exception e) {
            logger.error("代购订单 {} 费用计算失败: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new FeeCalculationException("代购订单费用计算失败", e);
        }
    }

    /**
     * 根据商品类型应用特定费率
     * 不同的商品类型有不同的配送难度和风险，因此应用不同的费率系数
     */
    private BigDecimal applyProductCategoryRate(FeeableOrder order, BigDecimal baseFee) {
        if (order instanceof PurchaseRequest purchaseRequest) {
            ProductCategory category = purchaseRequest.getCategory();

            if (category != null) {
                BigDecimal categoryRate = switch (category) {
                    case ELECTRONICS -> BigDecimal.valueOf(1.5); // 电子产品费率1.5倍，因为易碎且贵重
                    case MEDICINE -> BigDecimal.valueOf(1.3); // 药品费率1.3倍，因为有时效性和特殊存储需求
                    case FOOD -> BigDecimal.valueOf(1.2); // 食品费率1.2倍，因为可能需要保温或保鲜
                    case BOOKS -> BigDecimal.valueOf(1.1); // 图书费率1.1倍，因为较重但不易碎
                    case CLOTHING -> BigDecimal.valueOf(0.9); // 衣物费率0.9倍，因为通常较轻且不易碎
                    case DAILY_NECESSITIES -> BigDecimal.valueOf(1.1); // 日用品费率1.1倍
                    case BEAUTY -> BigDecimal.valueOf(1.2); // 美妆产品费率1.2倍
                    case SPORTS -> BigDecimal.valueOf(1.3); // 运动用品费率1.3倍
                    default -> BigDecimal.ONE; // 其他类别默认费率1.0
                };

                logger.debug("商品类别 {} 应用费率: {}", category, categoryRate);
                return baseFee.multiply(categoryRate).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return baseFee;
    }

    @Override
    public boolean validateFee(FeeableOrder order) {
        // 1. 验证商品预期价格
        if (order.getExpectedPrice().compareTo(getMaxExpectedPrice()) > 0) {
            return false;
        }

        // 2. 验证配送距离 - 增加null检查
        Double deliveryDistance = order.getDeliveryDistance();
        if (deliveryDistance != null && deliveryDistance > getMaxDeliveryDistance()) {
            return false;
        }

        // 3. 验证购买数量
        return order.getQuantity() <= getMaxQuantity();
    }

    @Override
    public FeeResult estimateFee(FeeableOrder order) {
        BigDecimal productFee = order.getExpectedPrice();
        // 添加基础费用计算
        BigDecimal baseFee = baseFeeCalculator.calculateBaseFee(order);
        // 应用商品类型特定费率
        baseFee = applyProductCategoryRate(order, baseFee);
        BigDecimal serviceFee = calculatePurchaseServiceFee(order);
        BigDecimal estimatedDelivery = deliveryFeeCalculator.calculateDeliveryFee(order);
        // 将基础费用加入到配送费中
        estimatedDelivery = estimatedDelivery.add(baseFee);

        return FeeResult.builder()
                .orderNumber(order.getOrderNumber())
                .productFee(productFee)
                .baseFee(baseFee)  // 添加基础费用
                .serviceFee(serviceFee)
                .deliveryFee(estimatedDelivery)
                .totalFee(productFee.add(serviceFee).add(estimatedDelivery))
                .build();
    }

    @Override
    public FeeType getSupportedFeeType() {
        return FeeType.PURCHASE_ORDER;
    }

    /**
     * 计算代购服务费
     * 根据商品预期价格计算服务费率
     */
    private BigDecimal calculatePurchaseServiceFee(FeeableOrder order) {
        BigDecimal expectedPrice = order.getExpectedPrice();

        // 根据预期价格确定费率
        BigDecimal serviceRate;
        if (expectedPrice.compareTo(BigDecimal.valueOf(100)) <= 0) {
            serviceRate = BigDecimal.valueOf(0.15); // 15%，低价商品相对收取更高服务费
        } else if (expectedPrice.compareTo(BigDecimal.valueOf(500)) <= 0) {
            serviceRate = BigDecimal.valueOf(0.12); // 12%，中等价格商品收取适中服务费
        } else {
            serviceRate = BigDecimal.valueOf(0.10); // 10%，高价商品收取较低服务费比例
        }

        return expectedPrice.multiply(serviceRate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getMaxExpectedPrice() {
        return BigDecimal.valueOf(2000); // 2000元，超过此价格的商品不适合代购系统
    }

    private double getMaxDeliveryDistance() {
        return 100.0; // 5公里，超过此距离的配送可能不适合普通代购
    }

    private int getMaxQuantity() {
        return 5; // 最多5件，限制单次代购的数量上限
    }
}