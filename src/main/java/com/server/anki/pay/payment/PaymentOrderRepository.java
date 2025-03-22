package com.server.anki.pay.payment;

import com.server.anki.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 支付订单数据访问接口
 * 提供支付订单的存取和查询功能
 */
@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    /**
     * 根据订单号查找支付订单
     */
    Optional<PaymentOrder> findByOrderNumber(String orderNumber);

    /**
     * 查找用户未支付的订单
     */
    List<PaymentOrder> findByUserAndStatus(User user, PaymentStatus status);

    /**
     * 查找已过期的待支付订单
     */
    List<PaymentOrder> findByStatusAndExpireTimeBefore(PaymentStatus status, LocalDateTime expiryTime);

    /**
     * 根据订单类型和订单信息查找支付订单
     */
    List<PaymentOrder> findByOrderTypeAndOrderInfoAndStatus(OrderType orderType, String orderInfo, PaymentStatus status);

    /**
     * 查找未支付的超时订单
     */
    @Query("SELECT p FROM PaymentOrder p WHERE p.status = 'WAITING' AND p.expireTime < :now")
    List<PaymentOrder> findExpiredPayments(@Param("now") LocalDateTime now);

    /**
     * 根据订单类型和订单信息查找有效的待支付订单
     */
    @Query("SELECT p FROM PaymentOrder p WHERE " +
            "p.orderType = :orderType AND p.orderInfo = :orderInfo " +
            "AND p.status = 'WAITING' AND p.expireTime > :now")
    List<PaymentOrder> findValidPaymentsByOrderInfo(
            @Param("orderType") OrderType orderType,
            @Param("orderInfo") String orderInfo,
            @Param("now") LocalDateTime now);
}