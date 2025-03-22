package com.server.anki.shopping.service.delivery;

import com.server.anki.amap.AmapService;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 平台配送策略实现
 * 实现了基于平台专职配送员的配送模式，包括智能调度、订单分配等功能
 */
@Slf4j
@Component
public class PlatformDeliveryStrategy implements DeliveryStrategy {

    private static final double MAX_DELIVERY_DISTANCE = 5.0; // 最大配送距离（公里）
    private static final int MAX_ACTIVE_ORDERS = 3; // 配送员最大同时配送订单数
    private static final int BASE_DELIVERY_TIME = 25; // 基础配送时间（分钟）

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmapService amapService;

    @Override
    public User assignDeliveryUser(ShoppingOrder order) {
        log.info("开始为订单{}分配平台配送员", order.getOrderNumber());

        // 获取所有平台配送员
        List<User> platformDeliverers = userRepository.findByUserGroup("platform_deliverer");

        // 智能分配算法：考虑距离、订单量和评分
        return platformDeliverers.stream()
                .filter(this::isDelivererAvailable)
                .filter(deliverer -> isWithinDeliveryRange(deliverer, order))
                .min(Comparator.comparingDouble(d -> calculateAssignmentScore(d, order)))
                .orElse(null);
    }

    @Override
    public LocalDateTime calculateExpectedDeliveryTime(ShoppingOrder order) {
        // 计算配送距离
        double distance = calculateDeliveryDistance(order);

        // 考虑高峰期因素
        int estimatedMinutes = BASE_DELIVERY_TIME + (int)(distance * 4); // 每公里增加4分钟
        if (isRushHour()) {
            estimatedMinutes = (int)(estimatedMinutes * 1.5); // 高峰期时间延长50%
        }

        return LocalDateTime.now().plusMinutes(estimatedMinutes);
    }

    @Override
    public boolean validateDeliveryRequest(ShoppingOrder order) {
        // 验证订单信息完整性
        if (!isOrderInfoComplete(order)) {
            log.warn("订单{}信息不完整，无法配送", order.getOrderNumber());
            return false;
        }

        // 验证配送距离
        double distance = calculateDeliveryDistance(order);
        if (distance > MAX_DELIVERY_DISTANCE) {
            log.warn("订单{}配送距离{}km超出平台配送范围", order.getOrderNumber(), distance);
            return false;
        }

        // 验证收货地址
        if (!isDeliveryAddressValid(order)) {
            log.warn("订单{}收货地址不在服务区域内", order.getOrderNumber());
            return false;
        }

        return true;
    }

    @Override
    public String getDeliveryTypeName() {
        return DeliveryType.PLATFORM.getLabel();
    }

    /**
     * 计算配送员分配得分
     * 得分越低越适合被分配
     */
    private double calculateAssignmentScore(User deliverer, ShoppingOrder order) {
        double distance = calculateDeliveryDistance(order);
        int activeOrders = getActiveOrderCount(deliverer);
        double rating = getDelivererRating(deliverer);

        // 综合考虑距离、当前订单量和评分
        return distance * 0.4 + activeOrders * 0.4 + (5 - rating) * 0.2;
    }

    /**
     * 检查是否处于配送高峰期
     */
    private boolean isRushHour() {
        int hour = LocalDateTime.now().getHour();
        return (hour >= 11 && hour <= 13) || (hour >= 17 && hour <= 19);
    }

    /**
     * 获取配送员评分
     */
    private double getDelivererRating(User deliverer) {
        // 实际项目中应该从评分系统获取配送员的评分
        return 4.5; // 简化实现
    }

    /**
     * 检查配送员是否可接单
     */
    private boolean isDelivererAvailable(User deliverer) {
        int activeOrders = getActiveOrderCount(deliverer);
        return activeOrders < MAX_ACTIVE_ORDERS;
    }

    /**
     * 检查配送员是否在配送范围内
     */
    private boolean isWithinDeliveryRange(User deliverer, ShoppingOrder order) {
        // 这里应该根据配送员实时位置判断
        return true; // 简化实现
    }

    /**
     * 获取配送员当前正在配送的订单数量
     */
    private int getActiveOrderCount(User deliverer) {
        // 实际项目中应该查询数据库获取配送员的在途订单数
        return 0; // 简化实现
    }

    /**
     * 计算配送距离
     */
    private double calculateDeliveryDistance(ShoppingOrder order) {
        return amapService.calculateWalkingDistance(
                order.getStore().getLatitude(),
                order.getStore().getLongitude(),
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude()
        ) / 1000.0; // 转换为公里
    }

    /**
     * 检查订单信息完整性
     */
    private boolean isOrderInfoComplete(ShoppingOrder order) {
        return Objects.nonNull(order.getDeliveryAddress()) &&
                Objects.nonNull(order.getDeliveryLatitude()) &&
                Objects.nonNull(order.getDeliveryLongitude()) &&
                Objects.nonNull(order.getRecipientName()) &&
                Objects.nonNull(order.getRecipientPhone());
    }

    /**
     * 验证收货地址是否在服务区域内
     */
    private boolean isDeliveryAddressValid(ShoppingOrder order) {
        // 实际项目中应该检查地址是否在服务范围内
        return true; // 简化实现
    }
}