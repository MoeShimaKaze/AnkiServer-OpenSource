package com.server.anki.fee.calculator;

import com.server.anki.amap.AmapService;
import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.marketing.SpecialDateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 配送费用计算器
 * 负责计算订单的配送相关费用,包括:
 * 1. 计算距离配送费
 * 2. 计算特殊时段配送费
 * 3. 计算特殊区域配送费
 */
@Component
public class DeliveryFeeCalculator {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryFeeCalculator.class);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private FeeConfiguration config;

    @Autowired
    private AmapService amapService;

    @Autowired
    private SpecialDateService specialDateService;

    /**
     * 计算配送费用
     */
    public BigDecimal calculateDeliveryFee(FeeableOrder order) {
        logger.debug("开始计算订单 {} 的配送费用", order.getOrderNumber());

        // 1. 计算基础配送距离费用
        BigDecimal distanceFee = calculateDistanceFee(order);

        // 2. 应用特殊时段费率
        distanceFee = applyDateFee(distanceFee, order);

        // 2. 应用特殊时段费率
        distanceFee = applyTimeRangeFee(distanceFee, order);

        // 3. 应用特殊区域费率
        distanceFee = applyRegionFee(distanceFee, order);

        logger.debug("订单 {} 配送费用计算完成: {}", order.getOrderNumber(), distanceFee);
        return distanceFee;
    }

    /**
     * 计算距离配送费
     */
    private BigDecimal calculateDistanceFee(FeeableOrder order) {
        // 计算配送距离
        double distance = calculateDeliveryDistance(order);

        // 获取基础免费配送距离
        double baseFreeDistance = config.getBaseFreeDistance(order.getFeeType());

        // 计算超出距离的费用
        if (distance > baseFreeDistance) {
            double extraDistance = distance - baseFreeDistance;
            BigDecimal ratePerKm = config.getDistanceRate(order.getFeeType());
            return BigDecimal.valueOf(extraDistance)
                    .multiply(ratePerKm)
                    .setScale(SCALE, ROUNDING_MODE);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 计算配送距离
     */
    private double calculateDeliveryDistance(FeeableOrder order) {
        return amapService.calculateWalkingDistance(
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude()
        ) / 1000.0; // 转换为公里
    }

    /**
     * 应用特殊时段费率
     */
    private BigDecimal applyTimeRangeFee(BigDecimal fee, FeeableOrder order) {
        BigDecimal timeMultiplier = specialDateService.getTimeRangeRateMultiplier(
                order.getCreatedTime().getHour(),
                order.getFeeType()
        );
        return fee.multiply(timeMultiplier).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 应用特殊日期费率
     */
    private BigDecimal applyDateFee(BigDecimal fee, FeeableOrder order) {
        // 添加空值检查
        if (order.getCreatedTime() == null) {
            logger.warn("订单创建时间为空，使用当前时间进行费率计算");
            BigDecimal timeMultiplier = specialDateService.getDateRateMultiplier(
                    LocalDateTime.now().toLocalDate(),
                    order.getFeeType()
            );
            return fee.multiply(timeMultiplier).setScale(SCALE, ROUNDING_MODE);
        }

        BigDecimal timeMultiplier = specialDateService.getDateRateMultiplier(
                order.getCreatedTime().toLocalDate(),
                order.getFeeType()
        );
        return fee.multiply(timeMultiplier).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 应用特殊区域费率
     */
    private BigDecimal applyRegionFee(BigDecimal fee, FeeableOrder order) {
        BigDecimal regionMultiplier = config.getRegionMultiplier(
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude(),
                order.getFeeType()
        );
        return fee.multiply(regionMultiplier).setScale(SCALE, ROUNDING_MODE);
    }
}