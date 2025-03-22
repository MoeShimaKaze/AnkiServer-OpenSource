package com.server.anki.shopping.service.delivery;

import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.user.User;

/**
 * 配送策略接口
 * 定义了配送服务的标准行为，包括配送员分配、订单状态更新等核心功能
 */
public interface DeliveryStrategy {

    /**
     * 分配配送员
     * 根据不同的配送策略选择合适的配送员
     *
     * @param order 需要配送的订单
     * @return 分配的配送员，如果暂时无法分配则返回null
     */
    User assignDeliveryUser(ShoppingOrder order);

    /**
     * 计算预计送达时间
     * 基于订单信息和当前时间估算送达时间
     *
     * @param order 订单信息
     * @return 预计送达的时间戳
     */
    java.time.LocalDateTime calculateExpectedDeliveryTime(ShoppingOrder order);

    /**
     * 验证订单是否可以配送
     * 检查订单状态、配送地址等信息是否满足配送条件
     *
     * @param order 待配送订单
     * @return 是否可以配送
     */
    boolean validateDeliveryRequest(ShoppingOrder order);

    /**
     * 获取配送类型名称
     * 用于日志记录和显示
     *
     * @return 配送类型的描述性名称
     */
    String getDeliveryTypeName();
}