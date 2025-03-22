package com.server.anki.timeout.service;

import com.server.anki.mailorder.entity.AbandonedOrder;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.repository.AbandonedOrderRepository;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.marketing.region.DeliveryRegion;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.marketing.repository.SpecialDateRepository;
import com.server.anki.marketing.repository.SpecialTimeRangeRepository;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.utils.MySQLSpatialUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单归档服务
 * 处理所有类型订单的归档操作
 */
@Service
public class OrderArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(OrderArchiveService.class);

    @Autowired
    private AbandonedOrderRepository abandonedOrderRepository;

    @Autowired
    private RegionService regionService;

    @Autowired
    private SpecialTimeRangeRepository specialTimeRangeRepository;

    @Autowired
    private SpecialDateRepository specialDateRepository;

    /**
     * 归档订单通用方法
     * 根据订单类型调用相应的归档处理方法
     */
    @Transactional
    public void archiveOrder(Timeoutable order) {
        logger.info("准备归档订单: {}", order.getOrderNumber());

        try {
            switch (order.getTimeoutOrderType()) {
                case MAIL_ORDER -> archiveMailOrder((MailOrder) order);
                case SHOPPING_ORDER -> archiveShoppingOrder((ShoppingOrder) order);
                case PURCHASE_REQUEST -> archivePurchaseRequest((PurchaseRequest) order);
                default -> throw new IllegalArgumentException("不支持的订单类型");
            }
            logger.info("订单归档成功, 订单号: {}", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("订单归档过程中发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("订单归档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 归档快递代拿订单
     * 修改：增强并发处理和日志记录
     */
    @Transactional
    public void archiveMailOrder(MailOrder order) {
        logger.info("开始归档快递代拿订单: {}", order.getOrderNumber());

        try {
            // 增加日志记录订单详情，便于排查问题
            if (logger.isDebugEnabled()) {
                logger.debug("归档订单详情: ID={}, Status={}, TimeoutCount={}, DeliveryService={}",
                        order.getId(), order.getOrderStatus(), order.getTimeoutCount(),
                        order.getDeliveryService());
            }

            // 使用悲观锁检查订单是否已经归档，避免并发问题
            List<AbandonedOrder> existingOrders = abandonedOrderRepository.findByOrderNumber(order.getOrderNumber());

            if (!existingOrders.isEmpty()) {
                logger.info("订单 {} 已经存在于归档表中，共 {} 条记录，进行更新",
                        order.getOrderNumber(), existingOrders.size());
                AbandonedOrder existingOrder = existingOrders.get(0);
                updateExistingAbandonedOrder(existingOrder, order);
            } else {
                // 创建新的废弃订单对象
                AbandonedOrder abandonedOrder = createAbandonedOrderFromMailOrder(order);
                // 保存归档订单
                abandonedOrderRepository.save(abandonedOrder);
            }

            logger.info("快递代拿订单 {} 归档成功", order.getOrderNumber());
        } catch (Exception e) {
            // 捕获并记录详细异常，方便排查
            logger.error("归档快递代拿订单 {} 时发生错误: {}", order.getOrderNumber(), e.getMessage(), e);
            throw e; // 重新抛出异常，让事务管理器决定是否回滚
        }
    }

    /**
     * 归档商品订单
     */
    @Transactional
    public void archiveShoppingOrder(ShoppingOrder order) {
        logger.info("开始归档商品订单: {}", order.getOrderNumber());

        // 检查订单是否已经归档
        List<AbandonedOrder> existingOrders = abandonedOrderRepository.findByOrderNumber(order.getOrderNumber());

        if (!existingOrders.isEmpty()) {
            logger.info("订单 {} 已经存在于归档表中，进行更新", order.getOrderNumber());
            AbandonedOrder existingOrder = existingOrders.get(0);
            updateExistingAbandonedOrderFromShopping(existingOrder, order);
        } else {
            // 创建新的废弃订单对象
            AbandonedOrder abandonedOrder = createAbandonedOrderFromShoppingOrder(order);
            // 保存归档订单
            abandonedOrderRepository.save(abandonedOrder);
        }

        logger.info("商品订单 {} 归档成功", order.getOrderNumber());
    }

    /**
     * 归档代购需求订单
     */
    @Transactional
    public void archivePurchaseRequest(PurchaseRequest request) {
        logger.info("开始归档代购需求订单: {}", request.getRequestNumber());

        // 检查订单是否已经归档
        List<AbandonedOrder> existingOrders = abandonedOrderRepository.findByOrderNumber(request.getRequestNumber());

        if (!existingOrders.isEmpty()) {
            logger.info("订单 {} 已经存在于归档表中，进行更新", request.getRequestNumber());
            AbandonedOrder existingOrder = existingOrders.get(0);
            updateExistingAbandonedOrderFromPurchase(existingOrder, request);
        } else {
            // 创建新的废弃订单对象
            AbandonedOrder abandonedOrder = createAbandonedOrderFromPurchaseRequest(request);
            // 保存归档订单
            abandonedOrderRepository.save(abandonedOrder);
        }

        logger.info("代购需求订单 {} 归档成功", request.getRequestNumber());
    }

    /**
     * 更新现有快递代拿废弃订单
     */
    private void updateExistingAbandonedOrder(AbandonedOrder existingOrder, MailOrder order) {
        // 复制基本信息
        copyBasicInformation(order, existingOrder);
        // 复制地址信息
        copyAddressInformation(order, existingOrder);
        // 复制订单状态和时间信息
        copyStatusAndTimeInformation(order, existingOrder);
        // 复制费用信息
        copyFeeInformation(order, existingOrder);
        // 复制区域信息
        copyRegionInformation(order, existingOrder);
        // 复制时段信息
        copyTimeRangeInformation(order, existingOrder);
        // 复制特殊日期信息
        copySpecialDateInformation(order, existingOrder);

        // 额外设置更新时间
        existingOrder.setClosedAt(LocalDateTime.now());

        // 保存更新后的订单
        abandonedOrderRepository.save(existingOrder);
    }

    /**
     * 更新现有商品订单废弃订单
     */
    private void updateExistingAbandonedOrderFromShopping(AbandonedOrder existingOrder, ShoppingOrder order) {
        // 设置基本字段
        existingOrder.setOrderNumber(order.getOrderNumber());
        existingOrder.setUser(order.getUser());
        existingOrder.setAssignedUser(order.getAssignedUser());
        existingOrder.setOrderStatus(order.getOrderStatus());
        existingOrder.setTimeoutStatus(order.getTimeoutStatus());
        existingOrder.setTimeoutWarningSent(order.isTimeoutWarningSent());
        existingOrder.setTimeoutCount(order.getTimeoutCount());

        // 设置地址信息
        existingOrder.setDeliveryAddress(order.getDeliveryAddress());
        existingOrder.setDeliveryLatitude(order.getDeliveryLatitude());
        existingOrder.setDeliveryLongitude(order.getDeliveryLongitude());

        // 设置时间信息
        existingOrder.setCreatedAt(order.getCreatedTime());
        existingOrder.setDeliveryTime(order.getExpectedDeliveryTime());
        existingOrder.setDeliveredDate(order.getDeliveredTime());
        existingOrder.setInterventionTime(order.getInterventionTime());
        existingOrder.setClosedAt(LocalDateTime.now());

        // 设置费用信息
        existingOrder.setFee(order.getTotalAmount().doubleValue());
        existingOrder.setPlatformIncome(order.getPlatformFee().doubleValue());

        // 保存更新后的订单
        abandonedOrderRepository.save(existingOrder);
    }

    /**
     * 更新现有代购需求废弃订单
     */
    private void updateExistingAbandonedOrderFromPurchase(AbandonedOrder existingOrder, PurchaseRequest request) {
        // 设置基本字段
        existingOrder.setOrderNumber(request.getRequestNumber());
        existingOrder.setUser(request.getUser());
        existingOrder.setAssignedUser(request.getAssignedUser());
        existingOrder.setOrderStatus(request.getStatus());
        existingOrder.setTimeoutStatus(request.getTimeoutStatus());
        existingOrder.setTimeoutWarningSent(request.isTimeoutWarningSent());
        existingOrder.setTimeoutCount(request.getTimeoutCount());

        // 设置地址信息
        existingOrder.setPickupAddress(request.getPurchaseAddress());
        existingOrder.setPickupLatitude(request.getPurchaseLatitude());
        existingOrder.setPickupLongitude(request.getPurchaseLongitude());
        existingOrder.setDeliveryAddress(request.getDeliveryAddress());
        existingOrder.setDeliveryLatitude(request.getDeliveryLatitude());
        existingOrder.setDeliveryLongitude(request.getDeliveryLongitude());

        // 设置时间信息
        existingOrder.setCreatedAt(request.getCreatedTime());
        existingOrder.setDeliveryTime(request.getExpectedDeliveryTime());
        existingOrder.setDeliveredDate(request.getDeliveredTime());
        existingOrder.setInterventionTime(request.getInterventionTime());
        existingOrder.setClosedAt(LocalDateTime.now());

        // 设置费用信息
        existingOrder.setFee(request.getTotalAmount().doubleValue());
        existingOrder.setUserIncome(request.getUserIncome());
        existingOrder.setPlatformIncome(request.getPlatformIncome());

        // 保存更新后的订单
        abandonedOrderRepository.save(existingOrder);
    }

    /**
     * 从快递代拿订单创建废弃订单
     */
    private AbandonedOrder createAbandonedOrderFromMailOrder(MailOrder order) {
        AbandonedOrder abandonedOrder = new AbandonedOrder();

        // 复制基本信息
        copyBasicInformation(order, abandonedOrder);
        // 复制地址信息
        copyAddressInformation(order, abandonedOrder);
        // 复制订单状态和时间信息
        copyStatusAndTimeInformation(order, abandonedOrder);
        // 复制费用信息
        copyFeeInformation(order, abandonedOrder);
        // 复制区域信息
        copyRegionInformation(order, abandonedOrder);
        // 复制时段信息
        copyTimeRangeInformation(order, abandonedOrder);
        // 复制特殊日期信息
        copySpecialDateInformation(order, abandonedOrder);

        return abandonedOrder;
    }

    /**
     * 从商品订单创建废弃订单
     */
    private AbandonedOrder createAbandonedOrderFromShoppingOrder(ShoppingOrder order) {
        AbandonedOrder abandonedOrder = new AbandonedOrder();

        // 设置基本字段
        abandonedOrder.setOrderNumber(order.getOrderNumber());
        abandonedOrder.setUser(order.getUser());
        abandonedOrder.setAssignedUser(order.getAssignedUser());
        abandonedOrder.setOrderStatus(order.getOrderStatus());
        abandonedOrder.setTimeoutStatus(order.getTimeoutStatus());
        abandonedOrder.setTimeoutWarningSent(order.isTimeoutWarningSent());
        abandonedOrder.setTimeoutCount(order.getTimeoutCount());

        // 设置名称信息
        abandonedOrder.setName(order.getProduct() != null ? order.getProduct().getName() : "商品订单");

        // 设置地址信息
        abandonedOrder.setDeliveryAddress(order.getDeliveryAddress());
        abandonedOrder.setDeliveryLatitude(order.getDeliveryLatitude());
        abandonedOrder.setDeliveryLongitude(order.getDeliveryLongitude());
        abandonedOrder.setDeliveryDetail(order.getRecipientName() + " " + order.getRecipientPhone());

        // 设置时间信息
        abandonedOrder.setCreatedAt(order.getCreatedTime());
        abandonedOrder.setDeliveryTime(order.getExpectedDeliveryTime());
        abandonedOrder.setDeliveredDate(order.getDeliveredTime());
        abandonedOrder.setInterventionTime(order.getInterventionTime());
        abandonedOrder.setClosedAt(LocalDateTime.now());

        // 设置费用信息
        abandonedOrder.setFee(order.getTotalAmount().doubleValue());
        abandonedOrder.setPlatformIncome(order.getPlatformFee().doubleValue());

        return abandonedOrder;
    }

    /**
     * 从代购需求创建废弃订单
     */
    private AbandonedOrder createAbandonedOrderFromPurchaseRequest(PurchaseRequest request) {
        AbandonedOrder abandonedOrder = new AbandonedOrder();

        // 设置基本字段
        abandonedOrder.setOrderNumber(request.getRequestNumber());
        abandonedOrder.setUser(request.getUser());
        abandonedOrder.setAssignedUser(request.getAssignedUser());
        abandonedOrder.setOrderStatus(request.getStatus());
        abandonedOrder.setTimeoutStatus(request.getTimeoutStatus());
        abandonedOrder.setTimeoutWarningSent(request.isTimeoutWarningSent());
        abandonedOrder.setTimeoutCount(request.getTimeoutCount());

        // 设置名称信息
        abandonedOrder.setName(request.getTitle());
        abandonedOrder.setContactInfo(request.getRecipientPhone());

        // 设置地址信息
        abandonedOrder.setPickupAddress(request.getPurchaseAddress());
        abandonedOrder.setPickupLatitude(request.getPurchaseLatitude());
        abandonedOrder.setPickupLongitude(request.getPurchaseLongitude());
        abandonedOrder.setDeliveryAddress(request.getDeliveryAddress());
        abandonedOrder.setDeliveryLatitude(request.getDeliveryLatitude());
        abandonedOrder.setDeliveryLongitude(request.getDeliveryLongitude());
        abandonedOrder.setDeliveryDetail(request.getRecipientName());

        // 设置时间信息
        abandonedOrder.setCreatedAt(request.getCreatedTime());
        abandonedOrder.setDeliveryTime(request.getExpectedDeliveryTime());
        abandonedOrder.setDeliveredDate(request.getDeliveredTime());
        abandonedOrder.setInterventionTime(request.getInterventionTime());
        abandonedOrder.setClosedAt(LocalDateTime.now());

        // 设置费用信息
        abandonedOrder.setFee(request.getTotalAmount().doubleValue());
        abandonedOrder.setUserIncome(request.getUserIncome());
        abandonedOrder.setPlatformIncome(request.getPlatformIncome());

        return abandonedOrder;
    }

    // 以下是从原AbandonedOrderService复制过来的方法

    private void copyBasicInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        abandonedOrder.setOrderNumber(order.getOrderNumber());
        abandonedOrder.setUser(order.getUser());
        abandonedOrder.setName(order.getName());
        abandonedOrder.setPickupCode(order.getPickupCode());
        abandonedOrder.setTrackingNumber(order.getTrackingNumber());
        abandonedOrder.setContactInfo(order.getContactInfo());
        abandonedOrder.setDeliveryService(order.getDeliveryService());
        abandonedOrder.setWeight(order.getWeight());
        abandonedOrder.setLargeItem(order.isLargeItem());
    }

    private void copyAddressInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        abandonedOrder.setPickupAddress(order.getPickupAddress());
        abandonedOrder.setPickupLatitude(order.getPickupLatitude());
        abandonedOrder.setPickupLongitude(order.getPickupLongitude());
        abandonedOrder.setPickupDetail(order.getPickupDetail());
        abandonedOrder.setDeliveryAddress(order.getDeliveryAddress());
        abandonedOrder.setDeliveryLatitude(order.getDeliveryLatitude());
        abandonedOrder.setDeliveryLongitude(order.getDeliveryLongitude());
        abandonedOrder.setDeliveryDetail(order.getDeliveryDetail());
        abandonedOrder.setDeliveryDistance(order.getDeliveryDistance());
    }

    private void copyStatusAndTimeInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        abandonedOrder.setOrderStatus(order.getOrderStatus());
        abandonedOrder.setCreatedAt(order.getCreatedAt());
        abandonedOrder.setClosedAt(LocalDateTime.now());
        abandonedOrder.setDeliveryTime(order.getDeliveryTime());
        abandonedOrder.setInterventionTime(order.getInterventionTime());
        abandonedOrder.setRefundRequestedAt(order.getRefundRequestedAt());
        abandonedOrder.setRefundDate(order.getRefundDate());
    }

    private void copyFeeInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        abandonedOrder.setFee(order.getFee());
        abandonedOrder.setUserIncome(order.getUserIncome());
        abandonedOrder.setPlatformIncome(order.getPlatformIncome());
        abandonedOrder.setRegionMultiplier(order.getRegionMultiplier());
    }

    /**
     * 复制区域信息
     * 增强版本：改进错误处理和坐标验证
     */
    private void copyRegionInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        // 初始化变量
        Optional<DeliveryRegion> pickupRegion = Optional.empty();
        Optional<DeliveryRegion> deliveryRegion = Optional.empty();

        try {
            // 安全处理取件点坐标
            if (order.getPickupLongitude() != null && order.getPickupLatitude() != null) {
                // 格式化坐标
                String pickupCoordinate = String.format("%.6f,%.6f",
                        order.getPickupLongitude(), order.getPickupLatitude());

                // 验证坐标
                if (MySQLSpatialUtils.validateCoordinate(pickupCoordinate)) {
                    try {
                        pickupRegion = regionService.findRegionByCoordinate(pickupCoordinate);
                    } catch (Exception e) {
                        logger.warn("查询取件点区域时发生错误: {}", e.getMessage());
                    }
                } else {
                    logger.warn("取件点坐标格式无效: {}", pickupCoordinate);
                }
            } else {
                logger.debug("取件点缺少经纬度信息");
            }

            // 安全处理配送点坐标
            if (order.getDeliveryLongitude() != null && order.getDeliveryLatitude() != null) {
                // 格式化坐标
                String deliveryCoordinate = String.format("%.6f,%.6f",
                        order.getDeliveryLongitude(), order.getDeliveryLatitude());

                // 验证坐标
                if (MySQLSpatialUtils.validateCoordinate(deliveryCoordinate)) {
                    try {
                        deliveryRegion = regionService.findRegionByCoordinate(deliveryCoordinate);
                    } catch (Exception e) {
                        logger.warn("查询配送点区域时发生错误: {}", e.getMessage());
                    }
                } else {
                    logger.warn("配送点坐标格式无效: {}", deliveryCoordinate);
                }
            } else {
                logger.debug("配送点缺少经纬度信息");
            }
        } catch (Exception e) {
            logger.warn("查询区域信息时发生错误: {}", e.getMessage());
            // 错误不中断归档流程
        }

        // 设置区域信息（即使找不到区域也能继续）
        abandonedOrder.setPickupRegionName(pickupRegion.map(DeliveryRegion::getName).orElse(null));
        abandonedOrder.setDeliveryRegionName(deliveryRegion.map(DeliveryRegion::getName).orElse(null));

        // 仅在两个区域都有效时才设置跨区域标志
        abandonedOrder.setCrossRegion(
                pickupRegion.isPresent() && deliveryRegion.isPresent() &&
                        !pickupRegion.get().getId().equals(deliveryRegion.get().getId())
        );
    }

    private void copyTimeRangeInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        if (order.getCreatedAt() != null) {
            int orderHour = order.getCreatedAt().getHour();
            List<SpecialTimeRange> timeRanges = specialTimeRangeRepository.findActiveByHour(orderHour);
            if (!timeRanges.isEmpty()) {
                SpecialTimeRange timeRange = timeRanges.get(0);
                abandonedOrder.setTimeRangeName(timeRange.getName());
                abandonedOrder.setTimeRangeRate(timeRange.getRateMultiplier());
            }
        }
    }

    private void copySpecialDateInformation(MailOrder order, AbandonedOrder abandonedOrder) {
        if (order.getCreatedAt() != null) {
            List<SpecialDate> specialDates = specialDateRepository
                    .findActiveAndRateEnabledByDate(order.getCreatedAt().toLocalDate());
            if (!specialDates.isEmpty()) {
                SpecialDate specialDate = specialDates.get(0);
                abandonedOrder.setSpecialDateName(specialDate.getName());
                abandonedOrder.setSpecialDateType(specialDate.getType());
                abandonedOrder.setSpecialDateRate(specialDate.getRateMultiplier());
            }
        }
    }
}