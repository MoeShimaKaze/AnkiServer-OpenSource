package com.server.anki.shopping.repository;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品订单数据访问接口
 * 提供商品订单信息的存取和查询功能
 */
@Repository
public interface ShoppingOrderRepository extends JpaRepository<ShoppingOrder, Long> {

    // 根据订单编号查找订单
    Optional<ShoppingOrder> findByOrderNumber(UUID orderNumber);

    // 查找用户的订单
    Page<ShoppingOrder> findByUser(User user, Pageable pageable);

    // 查找商家的订单
    Page<ShoppingOrder> findByStore(Store store, Pageable pageable);

    // 查找配送员的订单
    List<ShoppingOrder> findByAssignedUserAndOrderStatusIn(User deliveryUser, List<OrderStatus> statuses);

    /**
     * 查找特定店铺特定状态的订单
     * @param store 店铺
     * @param orderStatus 订单状态
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    Page<ShoppingOrder> findByStoreAndOrderStatus(Store store, OrderStatus orderStatus, Pageable pageable);

    // 统计商家的订单金额
    @Query("SELECT SUM(s.merchantIncome) FROM ShoppingOrder s WHERE s.store = :store " +
            "AND s.orderStatus = 'COMPLETED' AND s.createdAt BETWEEN :start AND :end")
    BigDecimal calculateStoreTotalIncome(@Param("store") Store store,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // 查找待支付的订单
    List<ShoppingOrder> findByOrderStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime time);

    // 查找需要分配配送员的订单
    List<ShoppingOrder> findByOrderStatusAndDeliveryTypeAndAssignedUserIsNull(
            OrderStatus status, DeliveryType deliveryType);

    // 统计各种配送方式的订单数量
    @Query("SELECT s.deliveryType, COUNT(s) FROM ShoppingOrder s " +
            "WHERE s.createdAt BETWEEN :start AND :end GROUP BY s.deliveryType")
    List<Object[]> countByDeliveryType(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    // 查找超时未送达的订单
    @Query("SELECT s FROM ShoppingOrder s WHERE s.orderStatus = 'IN_TRANSIT' " +
            "AND s.expectedDeliveryTime < :now")
    List<ShoppingOrder> findOverdueOrders(@Param("now") LocalDateTime now);

    /**
     * 查找特定用户的特定状态的订单
     * @param user 用户
     * @param orderStatus 订单状态
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    Page<ShoppingOrder> findByUserAndOrderStatus(User user, OrderStatus orderStatus, Pageable pageable);

    /**
     * 查找特定状态的订单
     * @param orderStatus 订单状态
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    Page<ShoppingOrder> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

    /**
     * 查找特定状态和配送类型的订单
     * @param orderStatus 订单状态
     * @param deliveryType 配送类型
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    Page<ShoppingOrder> findByOrderStatusAndDeliveryType(OrderStatus orderStatus, DeliveryType deliveryType, Pageable pageable);

    /**
     * 查找附近可接单的订单
     * @param latitude 纬度
     * @param longitude 经度
     * @param distance 距离（公里）
     * @param deliveryType 配送类型（可选）
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    @Query("SELECT s FROM ShoppingOrder s WHERE " +
            "s.orderStatus = 'PENDING' AND " +
            "(:deliveryType IS NULL OR s.deliveryType = :deliveryType) AND " +
            "6371 * acos(cos(radians(:latitude)) * cos(radians(s.deliveryLatitude)) * " +
            "cos(radians(s.deliveryLongitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(s.deliveryLatitude))) < :distance " +
            "ORDER BY 6371 * acos(cos(radians(:latitude)) * cos(radians(s.deliveryLatitude)) * " +
            "cos(radians(s.deliveryLongitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(s.deliveryLatitude))) ASC")
    Page<ShoppingOrder> findNearbyAvailableOrders(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("distance") Double distance,
            @Param("deliveryType") String deliveryType,
            Pageable pageable);

    /**
     * 根据订单状态和创建时间范围查询订单
     * @param orderStatus 订单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的订单列表
     */
    List<ShoppingOrder> findByOrderStatusAndCreatedAtBetween(
            OrderStatus orderStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 根据分配的用户、订单状态和创建时间范围查询订单
     * @param assignedUser 分配的用户
     * @param orderStatus 订单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的订单列表
     */
    List<ShoppingOrder> findByAssignedUserAndOrderStatusAndCreatedAtBetween(
            User assignedUser,
            OrderStatus orderStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 计算指定用户在特定时间范围内的订单数量
     * @param assignedUser 分配的用户
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 订单数量
     */
    int countByAssignedUserAndCreatedAtBetween(
            User assignedUser,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 按超时状态和创建时间范围查询订单
     * @param timeoutStatus 超时状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的订单列表
     */
    List<ShoppingOrder> findByTimeoutStatusAndCreatedAtBetween(
            com.server.anki.timeout.enums.TimeoutStatus timeoutStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询特定时间段内超时次数大于指定值的订单
     * @param minTimeoutCount 最小超时次数
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的订单列表
     */
    List<ShoppingOrder> findByTimeoutCountGreaterThanAndCreatedAtBetween(
            int minTimeoutCount,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 根据用户和多个订单状态查询订单
     * @param user 用户
     * @param orderStatusList 订单状态列表
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    Page<ShoppingOrder> findByUserAndOrderStatusIn(User user, List<OrderStatus> orderStatusList, Pageable pageable);

    // 根据订单状态查询
    List<ShoppingOrder> findByOrderStatus(OrderStatus status);

    // 根据创建时间查询订单
    List<ShoppingOrder> findByCreatedAtAfter(LocalDateTime date);

    // 根据店铺和创建时间查询订单
    Page<ShoppingOrder> findByStoreAndCreatedAtAfter(Store store, LocalDateTime date, Pageable pageable);

    // 修改 findByProduct 方法签名，添加 Pageable 参数
    Page<ShoppingOrder> findByProduct(Product product, Pageable pageable);

    // 根据店铺ID查询订单
    @Query("SELECT s FROM ShoppingOrder s WHERE s.store.id = :storeId")
    List<ShoppingOrder> findByStoreId(@Param("storeId") Long storeId);
}