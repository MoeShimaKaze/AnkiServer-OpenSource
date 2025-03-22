package com.server.anki.mailorder.repository;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.OrderStatus;
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

@Repository
public interface MailOrderRepository extends JpaRepository<MailOrder, Long> {
    // 基本查询方法
    List<MailOrder> findByOrderStatus(OrderStatus orderStatus);
    Optional<MailOrder> findByOrderNumber(UUID orderNumber);

    // 时间范围查询
    List<MailOrder> findByAssignedUserAndCreatedAtBetween(
            User user,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<MailOrder> findByCreatedAtBetween(
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    // 超时统计相关查询
    List<MailOrder> findByOrderStatusAndCreatedAtBetweenOrderByAssignedUserIdAsc(
            OrderStatus orderStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    int countByAssignedUserIdAndOrderStatus(
            Long userId,
            OrderStatus orderStatus
    );

    int countByAssignedUserIdAndCreatedAtBetween(
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    // 计算超时费用
    @Query("SELECT SUM(m.platformIncome) FROM MailOrder m " +
            "WHERE m.assignedUser.id = :userId " +
            "AND m.orderStatus = :orderStatus " +
            "AND m.createdAt BETWEEN :startTime AND :endTime")
    BigDecimal calculateTimeoutFees(
            @Param("userId") Long userId,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<MailOrder> findByAssignedUserIdAndOrderStatusAndCreatedAtBetween(
            Long userId,
            OrderStatus orderStatus,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 根据订单状态和创建时间范围查询订单
     * @param orderStatus 订单状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的订单列表
     */
    List<MailOrder> findByOrderStatusAndCreatedAtBetween(
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
    List<MailOrder> findByAssignedUserAndOrderStatusAndCreatedAtBetween(
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

    // 添加支持分页的查询方法
    Page<MailOrder> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);
    Page<MailOrder> findByUserId(Long userId, Pageable pageable);
    Page<MailOrder> findByAssignedUserId(Long assignedUserId, Pageable pageable);
    Page<MailOrder> findByAssignedUserIdAndOrderStatusIn(Long userId, List<OrderStatus> statuses, Pageable pageable);

}