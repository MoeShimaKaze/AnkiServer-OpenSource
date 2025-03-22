package com.server.anki.shopping.repository;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 代购需求数据访问接口
 * 提供代购需求信息的存取和查询功能，支持订单状态管理和支付流程
 */
@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    /**
     * 根据需求编号查找代购需求
     * 用于支付流程中的订单查询和状态更新
     */
    Optional<PurchaseRequest> findByRequestNumber(UUID requestNumber);

    /**
     * 查找用户发布的所有需求
     * 用于用户查看自己的历史需求
     */
    List<PurchaseRequest> findByUser(User user);

    /**
     * 分页查询用户的需求
     * 支持分页查看用户的需求列表
     */
    Page<PurchaseRequest> findByUser(User user, Pageable pageable);

    /**
     * 根据状态查询用户的需求
     * 支持按状态筛选用户的需求
     */
    Page<PurchaseRequest> findByUserAndStatus(User user, OrderStatus status, Pageable pageable);

    /**
     * 查找即将到期的需求
     * 用于系统自动处理超时订单，只查询待支付和待接单状态的订单
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE p.deadline BETWEEN :now AND :future " +
            "AND p.status IN ('PAYMENT_PENDING', 'PENDING')")
    List<PurchaseRequest> findUpcomingDeadlines(
            @Param("now") LocalDateTime now,
            @Param("future") LocalDateTime future);

    /**
     * 搜索代购需求
     * 支持按关键词和状态筛选，用于需求市场展示
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE " +
            "(p.title LIKE %:keyword% OR p.description LIKE %:keyword%) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<PurchaseRequest> searchRequests(
            @Param("keyword") String keyword,
            @Param("status") OrderStatus status,
            Pageable pageable);

    /**
     * 高级搜索代购需求
     * 支持多条件筛选，包括配送方式、价格区间等
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE " +
            "(p.title LIKE %:keyword% OR p.description LIKE %:keyword%) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:deliveryType IS NULL OR p.deliveryType = :deliveryType) " +
            "AND (:minPrice IS NULL OR p.expectedPrice >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.expectedPrice <= :maxPrice)")
    Page<PurchaseRequest> searchRequestsAdvanced(
            @Param("keyword") String keyword,
            @Param("status") OrderStatus status,
            @Param("deliveryType") DeliveryType deliveryType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /**
     * 统计用户的进行中需求数量
     * 包括待支付、待接单、进行中的订单，用于限制用户同时进行的订单数量
     */
    @Query("SELECT COUNT(p) FROM PurchaseRequest p WHERE p.user = :user " +
            "AND p.status IN ('PAYMENT_PENDING', 'PENDING', 'ASSIGNED', 'IN_TRANSIT')")
    long countActiveRequests(@Param("user") User user);

    /**
     * 查找配送员正在处理的需求
     * 用于配送员查看自己正在处理的订单
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE p.assignedUser = :user " +
            "AND p.status IN ('ASSIGNED', 'IN_TRANSIT')")
    List<PurchaseRequest> findActiveRequestsByDeliveryUser(@Param("user") User user);

    /**
     * 查找超时未支付的需求
     * 用于系统自动取消超时未支付的订单
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE p.status = 'PAYMENT_PENDING' " +
            "AND p.createdAt <= :deadline")
    List<PurchaseRequest> findPaymentTimeoutRequests(@Param("deadline") LocalDateTime deadline);

    /**
     * 查找指定区域内的待接单需求
     * 用于展示配送员附近的可接单需求
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE p.status = 'PENDING' " +
            "AND p.purchaseLatitude BETWEEN :minLat AND :maxLat " +
            "AND p.purchaseLongitude BETWEEN :minLng AND :maxLng")
    List<PurchaseRequest> findNearbyRequests(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    /**
     * 统计各状态的需求数量
     * 用于统计分析和数据展示
     */
    @Query("SELECT p.status, COUNT(p) FROM PurchaseRequest p GROUP BY p.status")
    List<Object[]> countRequestsByStatus();

    /**
     * 查找需要退款的需求
     * 用于处理退款申请
     */
    List<PurchaseRequest> findByStatusAndRefundStatus(
            OrderStatus orderStatus,
            String refundStatus);

    /**
     * 根据状态和创建时间范围查询代购需求
     * @param status 订单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的代购需求列表
     */
    List<PurchaseRequest> findByStatusAndCreatedAtBetween(
            OrderStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 根据分配的用户、状态和创建时间范围查询代购需求
     * @param assignedUser 分配的用户
     * @param status 订单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的代购需求列表
     */
    List<PurchaseRequest> findByAssignedUserAndStatusAndCreatedAtBetween(
            User assignedUser,
            OrderStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 计算指定用户在特定时间范围内的代购需求数量
     * @param assignedUser 分配的用户
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 代购需求数量
     */
    int countByAssignedUserAndCreatedAtBetween(
            User assignedUser,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 按超时状态和创建时间范围查询代购需求
     * @param timeoutStatus 超时状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的代购需求列表
     */
    List<PurchaseRequest> findByTimeoutStatusAndCreatedAtBetween(
            TimeoutStatus timeoutStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询特定时间段内超时次数大于指定值的代购需求
     * @param minTimeoutCount 最小超时次数
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的代购需求列表
     */
    List<PurchaseRequest> findByTimeoutCountGreaterThanAndCreatedAtBetween(
            int minTimeoutCount,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询所有已分配给指定用户的代购需求
     * @param assignedUser 分配的用户
     * @return 符合条件的代购需求列表
     */
    List<PurchaseRequest> findByAssignedUser(User assignedUser);

    /**
     * 更新浏览量
     * @param id 需求ID
     */
    @Modifying
    @Transactional
    @Query("UPDATE PurchaseRequest p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /**
     * 根据用户位置查询附近的需求
     * @param minLat 最小纬度
     * @param maxLat 最大纬度
     * @param minLng 最小经度
     * @param maxLng 最大经度
     * @param status 需求状态（可选）
     * @return 需求列表
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE " +
            "p.purchaseLatitude BETWEEN :minLat AND :maxLat AND " +
            "p.purchaseLongitude BETWEEN :minLng AND :maxLng AND " +
            "(:status IS NULL OR p.status = :status) AND " +
            "p.status IN ('PENDING')")
    List<PurchaseRequest> findNearbyRequestsForRecommendation(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("status") OrderStatus status);

    /**
     * 查询所有可接单的互助配送代购需求
     * 支持分页查询，按创建时间倒序排列
     * @param pageable 分页参数
     * @return 分页的可接单互助配送代购需求列表
     */
    @Query("SELECT p FROM PurchaseRequest p WHERE p.status = 'PENDING' " +
            "AND p.deliveryType = 'MUTUAL' ORDER BY p.createdAt DESC")
    Page<PurchaseRequest> findAvailableMutualRequests(Pageable pageable);

    // 添加这个新方法，查找状态为PENDING且已超过截止时间的代购需求
    List<PurchaseRequest> findByStatusAndDeadlineBefore(OrderStatus status, LocalDateTime deadline);

}