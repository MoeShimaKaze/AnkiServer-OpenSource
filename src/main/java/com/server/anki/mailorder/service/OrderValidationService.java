package com.server.anki.mailorder.service;

import com.server.anki.amap.AmapService;
import com.server.anki.config.MailOrderConfig;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.OrderValidationResult;
import com.server.anki.utils.MySQLSpatialUtils;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.marketing.region.model.RegionRateResult;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 订单验证服务
 * 负责验证订单的各项参数是否符合要求
 */
@Slf4j
@Service
public class OrderValidationService {

    @Autowired
    private MailOrderConfig mailOrderConfig;

    @Autowired
    private AmapService amapService;

    @Autowired
    private RegionService regionService;

    /**
     * 执行完整的订单验证
     * 按照业务重要性顺序依次验证各项规则
     */
    public OrderValidationResult validateOrderCompletely(MailOrder mailOrder) {
        try {
            log.debug("开始完整验证订单: {}", mailOrder.getOrderNumber());

            // 1. 验证必填字段
            OrderValidationResult requiredFieldsResult = validateRequiredFields(mailOrder);
            if (!requiredFieldsResult.isValid()) {
                return requiredFieldsResult;
            }

            // 2. 验证地址信息
            OrderValidationResult addressResult = validateAddressFields(mailOrder);
            if (!addressResult.isValid()) {
                return addressResult;
            }

            // 3. 验证订单约束（重量、距离等）
            OrderValidationResult constraintsResult = validateOrder(mailOrder);
            if (!constraintsResult.isValid()) {
                return constraintsResult;
            }

            // 4. 验证订单时间
            OrderValidationResult timeResult = validateOrderTime(mailOrder);
            if (!timeResult.isValid()) {
                return timeResult;
            }

            return OrderValidationResult.success();

        } catch (Exception e) {
            log.error("订单验证过程中发生错误: ", e);
            return OrderValidationResult.failure("订单验证过程中发生错误: " + e.getMessage());
        }
    }

    /**
     * 验证订单必填字段
     */
    public OrderValidationResult validateRequiredFields(MailOrder mailOrder) {
        log.debug("验证订单{}的必填字段", mailOrder.getOrderNumber());

        // 验证用户信息
        if (mailOrder.getUser() == null) {
            return OrderValidationResult.failure("缺少订单创建者信息");
        }

        // 验证基本信息
        if (StringUtils.isBlank(mailOrder.getName())) {
            return OrderValidationResult.failure("订单名称不能为空");
        }

        // 验证联系信息
        if (StringUtils.isBlank(mailOrder.getContactInfo())) {
            return OrderValidationResult.failure("联系方式不能为空");
        }

        // 验证配送服务类型
        if (mailOrder.getDeliveryService() == null) {
            return OrderValidationResult.failure("必须选择配送服务类型");
        }

        // 验证重量信息
        if (mailOrder.getWeight() <= 0) {
            return OrderValidationResult.failure("包裹重量必须大于0");
        }

        return OrderValidationResult.success();
    }

    /**
     * 验证订单的地址相关字段
     */
    public OrderValidationResult validateAddressFields(MailOrder mailOrder) {
        log.debug("验证订单{}的地址信息", mailOrder.getOrderNumber());

        // 验证取件地址坐标
        if (mailOrder.getPickupLatitude() == null || mailOrder.getPickupLongitude() == null) {
            return OrderValidationResult.failure("取件地址坐标不能为空");
        }

        String pickupCoordinate = String.format("%.6f,%.6f",
                mailOrder.getPickupLongitude(), mailOrder.getPickupLatitude());
        if (!MySQLSpatialUtils.validateCoordinate(pickupCoordinate)) {
            return OrderValidationResult.failure("取件地址坐标格式无效");
        }

        // 验证配送地址坐标
        if (mailOrder.getDeliveryLatitude() == null || mailOrder.getDeliveryLongitude() == null) {
            return OrderValidationResult.failure("配送地址坐标不能为空");
        }

        String deliveryCoordinate = String.format("%.6f,%.6f",
                mailOrder.getDeliveryLongitude(), mailOrder.getDeliveryLatitude());
        if (!MySQLSpatialUtils.validateCoordinate(deliveryCoordinate)) {
            return OrderValidationResult.failure("配送地址坐标格式无效");
        }

        // 验证地址文本
        if (StringUtils.isBlank(mailOrder.getPickupAddress()) ||
                StringUtils.isBlank(mailOrder.getPickupDetail())) {
            return OrderValidationResult.failure("取件地址信息不完整");
        }

        if (StringUtils.isBlank(mailOrder.getDeliveryAddress()) ||
                StringUtils.isBlank(mailOrder.getDeliveryDetail())) {
            return OrderValidationResult.failure("配送地址信息不完整");
        }

        return OrderValidationResult.success();
    }

    /**
     * 验证订单约束条件
     */
    private OrderValidationResult validateOrder(MailOrder order) {
        log.debug("验证订单{}的约束条件", order.getOrderNumber());

        // 获取服务配置
        MailOrderConfig.ServiceConfig config =
                mailOrderConfig.getServiceConfig(order.getDeliveryService());

        // 1. 验证重量限制
        OrderValidationResult weightResult = validateWeight(order, config);
        if (!weightResult.isValid()) {
            return weightResult;
        }

        // 2. 根据配送类型执行不同的验证
        if (order.getDeliveryService() == DeliveryService.EXPRESS) {
            return validateExpressOrder(order, config);
        } else {
            return validateStandardOrder(order, config);
        }
    }

    /**
     * 验证快递订单
     */
    private OrderValidationResult validateExpressOrder(MailOrder order,
                                                       MailOrderConfig.ServiceConfig config) {
        log.debug("验证快递订单: {}", order.getOrderNumber());

        // 1. 验证配送距离
        OrderValidationResult distanceResult = validateDeliveryDistance(order, config);
        if (!distanceResult.isValid()) {
            return distanceResult;
        }

        // 2. 计算区域费率
        String pickupCoordinate = formatCoordinate(
                order.getPickupLongitude(),
                order.getPickupLatitude()
        );
        String deliveryCoordinate = formatCoordinate(
                order.getDeliveryLongitude(),
                order.getDeliveryLatitude()
        );

        RegionRateResult regionRate = regionService.calculateOrderRegionRate(
                pickupCoordinate,
                deliveryCoordinate
        );

        // 设置区域费率到订单中
        order.setRegionMultiplier(regionRate.finalRate());

        String message = "快递订单验证通过. " + regionRate.getDescription();
        if (regionRate.isCrossRegion()) {
            message += " (跨区域配送)";
        }

        return OrderValidationResult.success();
    }

    /**
     * 验证标准订单
     */
    private OrderValidationResult validateStandardOrder(MailOrder order,
                                                        MailOrderConfig.ServiceConfig config) {
        log.debug("验证标准订单: {}", order.getOrderNumber());
        return validateDeliveryDistance(order, config);
    }

    /**
     * 验证订单重量是否符合限制
     */
    private OrderValidationResult validateWeight(MailOrder order,
                                                 MailOrderConfig.ServiceConfig config) {
        if (order.getWeight() <= 0) {
            return OrderValidationResult.failure("订单重量必须大于0");
        }

        if (order.getWeight() > config.getMaxWeight()) {
            return OrderValidationResult.failure(
                    String.format(
                            "包裹重量超过限制: %.2f kg (最大允许: %.2f kg)",
                            order.getWeight(),
                            config.getMaxWeight()
                    )
            );
        }

        return OrderValidationResult.success();
    }

    /**
     * 验证配送距离是否符合限制
     */
    private OrderValidationResult validateDeliveryDistance(MailOrder order,
                                                           MailOrderConfig.ServiceConfig config) {
        // 使用高德地图API计算实际配送距离
        double distance = amapService.calculateWalkingDistance(
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDeliveryLatitude(),
                order.getDeliveryLongitude()
        );

        // 设置订单的配送距离
        order.setDeliveryDistance(distance / 1000.0); // 转换为公里

        // 验证是否超过最大配送距离
        if (distance > config.getMaxDistance()) {
            return OrderValidationResult.failure(
                    String.format(
                            "配送距离超过限制: %.2f 米 (最大允许: %.2f 米)",
                            distance,
                            config.getMaxDistance()
                    )
            );
        }

        return OrderValidationResult.success();
    }

    /**
     * 验证订单时间限制
     */
    private OrderValidationResult validateOrderTime(MailOrder order) {
        LocalDateTime orderTime = order.getDeliveryTime();
        if (orderTime == null) {
            return OrderValidationResult.failure("配送时间不能为空");
        }

        // 获取营业时间配置
        MailOrderConfig.BusinessHoursConfig businessConfig = mailOrderConfig.getBusinessHours();

        // 验证是否在营业时间内
        if (businessConfig.isEnableBusinessHourCheck()) {
            LocalTime time = orderTime.toLocalTime();
            LocalTime openTime = LocalTime.of(businessConfig.getOpenHour(), 0);
            LocalTime closeTime = LocalTime.of(businessConfig.getCloseHour(), 0);

            if (time.isBefore(openTime) || time.isAfter(closeTime)) {
                return OrderValidationResult.failure(businessConfig.getBusinessHourErrorMsg());
            }
        }

        // 验证预约时间是否合理
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxFutureTime = now.plusDays(businessConfig.getMaxAdvanceBookingDays());

        if (orderTime.isBefore(now)) {
            return OrderValidationResult.failure(businessConfig.getPastTimeErrorMsg());
        }

        if (orderTime.isAfter(maxFutureTime)) {
            return OrderValidationResult.failure(businessConfig.getAdvanceBookingErrorMsg());
        }

        return OrderValidationResult.success();
    }

    /**
     * 格式化坐标字符串
     */
    private String formatCoordinate(Double longitude, Double latitude) {
        return String.format("%.6f,%.6f", longitude, latitude);
    }
}