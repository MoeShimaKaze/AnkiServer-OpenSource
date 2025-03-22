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
import java.util.List;
import java.util.Objects;
import java.util.Comparator;

/**
 * 互助配送策略实现
 * 实现了基于同学互助的配送模式，包括就近配送员分配、智能时间预估等功能
 */
@Slf4j
@Component
public class MutualDeliveryStrategy implements DeliveryStrategy {

    private static final double MAX_DELIVERY_DISTANCE = 3.0; // 最大配送距离（公里）
    private static final int MAX_ACTIVE_ORDERS = 2; // 配送员最大同时配送订单数
    private static final int BASE_DELIVERY_TIME = 30; // 基础配送时间（分钟）

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmapService amapService;

    @Override
    public User assignDeliveryUser(ShoppingOrder order) {
        log.info("开始为订单{}分配互助配送员", order.getOrderNumber());

        // 获取所有可用的配送员
        List<User> availableDeliverers = userRepository.findByUserGroup("deliverer");

        // 根据位置和当前负载筛选合适的配送员
        return availableDeliverers.stream()
                .filter(this::isDelivererAvailable)
                .filter(deliverer -> isWithinDeliveryRange(deliverer, order))
                .min(Comparator.comparingInt(this::getActiveOrderCount))
                .orElse(null);
    }

    @Override
    public LocalDateTime calculateExpectedDeliveryTime(ShoppingOrder order) {
        // 计算配送距离
        double distance = calculateDeliveryDistance(order);

        // 基于距离和当前时间估算送达时间
        int estimatedMinutes = BASE_DELIVERY_TIME + (int)(distance * 5); // 每公里增加5分钟

        return LocalDateTime.now().plusMinutes(estimatedMinutes);
    }

    @Override
    public boolean validateDeliveryRequest(ShoppingOrder order) {
        // 验证订单信息完整性
        if (!isOrderInfoComplete(order)) {
            log.warn("订单{}信息不完整，无法配送", order.getOrderNumber());
            return false;
        }

        // 验证配送距离是否在范围内
        double distance = calculateDeliveryDistance(order);
        if (distance > MAX_DELIVERY_DISTANCE) {
            log.warn("订单{}配送距离{}km超出互助配送范围", order.getOrderNumber(), distance);
            return false;
        }

        // 验证收货地址是否在服务区域内
        if (!isDeliveryAddressValid(order)) {
            log.warn("订单{}收货地址不在服务区域内", order.getOrderNumber());
            return false;
        }

        return true;
    }

    @Override
    public String getDeliveryTypeName() {
        return DeliveryType.MUTUAL.getLabel();
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
        // 这里可以根据配送员最后位置和订单配送地址计算距离
        // 实际项目中应该考虑配送员实时位置信息
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
        // 实际项目中应该检查地址是否在学校服务范围内
        return true; // 简化实现
    }
}