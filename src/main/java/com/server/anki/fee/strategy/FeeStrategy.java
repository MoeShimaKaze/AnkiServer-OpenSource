package com.server.anki.fee.strategy;

import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.FeeableOrder;
import com.server.anki.fee.result.FeeResult;

/**
 * 费用计算策略接口
 * 定义了订单费用计算的标准流程
 */
public interface FeeStrategy {

    /**
     * 计算订单费用
     * @param order 订单信息
     * @return 费用计算结果
     */
    FeeResult calculateFee(FeeableOrder order);

    /**
     * 验证订单费用
     * @param order 订单信息
     * @return 是否通过验证
     */
    boolean validateFee(FeeableOrder order);

    /**
     * 估算订单费用
     * @param order 订单信息
     * @return 预估费用结果
     */
    FeeResult estimateFee(FeeableOrder order);

    /**
     * 获取策略支持的订单类型
     * @return 费用类型
     */
    FeeType getSupportedFeeType();
}