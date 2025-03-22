package com.server.anki.fee.core;

import com.server.anki.fee.exception.FeeCalculationException;
import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeDistribution;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.fee.strategy.FeeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 费用计算上下文
 * 统一管理费用计算的入口,负责策略的选择和执行
 */
@Component
public class FeeContext {
    private static final Logger logger = LoggerFactory.getLogger(FeeContext.class);

    private final Map<FeeType, FeeStrategy> strategies = new ConcurrentHashMap<>();

    @Autowired
    private FeeConfiguration config;

    /**
     * 注册费用计算策略
     */
    @Autowired
    public void registerStrategies(List<FeeStrategy> strategyList) {
        strategyList.forEach(strategy ->
                strategies.put(strategy.getSupportedFeeType(), strategy));
    }

    /**
     * 计算订单费用
     */
    public FeeResult calculateFee(FeeableOrder order) {
        try {
            // 获取对应的计费策略
            FeeStrategy strategy = getStrategy(order.getFeeType());

            // 验证订单费用
            if (!strategy.validateFee(order)) {
                throw new FeeCalculationException("订单费用验证失败");
            }

            // 执行费用计算
            FeeResult result = strategy.calculateFee(order);

            // 验证计算结果
            if (!isValidFeeResult(result)) {
                throw new FeeCalculationException("费用计算结果无效");
            }

            return result;
        } catch (Exception e) {
            logger.error("订单 {} 费用计算失败: {}", order.getOrderNumber(), e.getMessage(), e);
            // 返回一个默认的费用结果
            return createDefaultFeeResult(order);
        }
    }

    private boolean isValidFeeResult(FeeResult result) {
        return result != null &&
                result.getTotalFee() != null &&
                result.getTotalFee().compareTo(BigDecimal.ZERO) > 0;
    }

    private FeeResult createDefaultFeeResult(FeeableOrder order) {
        // 创建默认的费用分配
        FeeDistribution defaultDistribution = new FeeDistribution(
                BigDecimal.valueOf(8), // 配送员收入
                BigDecimal.valueOf(2), // 平台收入
                BigDecimal.ZERO  // 商家收入(代购订单没有商家)
        );

        return FeeResult.builder()
                .orderNumber(order.getOrderNumber())
                .baseFee(BigDecimal.valueOf(10))
                .deliveryFee(BigDecimal.valueOf(8))
                .serviceFee(BigDecimal.valueOf(2))
                .totalFee(BigDecimal.valueOf(10).add(order.getExpectedPrice()))
                .distribution(defaultDistribution) // 设置非空分配
                .build();
    }

    /**
     * 估算订单费用
     */
    public FeeResult estimateFee(FeeableOrder order) {
        logger.info("开始估算订单 {} 的费用", order.getOrderNumber());
        return getStrategy(order.getFeeType()).estimateFee(order);
    }

    /**
     * 获取费用计算策略
     */
    private FeeStrategy getStrategy(FeeType feeType) {
        FeeStrategy strategy = strategies.get(feeType);
        if (strategy == null) {
            throw new FeeCalculationException("未找到对应的费用计算策略: " + feeType);
        }
        return strategy;
    }

    /**
     * 验证订单费用
     */
    public boolean validateFee(FeeableOrder order) {
        return getStrategy(order.getFeeType()).validateFee(order);
    }

    /**
     * 获取费用配置
     */
    public FeeConfiguration getConfiguration() {
        return config;
    }
}