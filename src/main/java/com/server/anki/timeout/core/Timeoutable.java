package com.server.anki.timeout.core;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 可超时接口
 * 所有需要超时管理的实体都应实现此接口
 */
public interface Timeoutable {
    /**
     * 获取订单编号
     * @return 订单唯一编号
     */
    UUID getOrderNumber();

    /**
     * 获取订单ID（数据库主键）
     * @return 订单在数据库中的ID
     */
    Long getId();

    /**
     * 获取订单创建时间
     * @return 创建时间
     */
    LocalDateTime getCreatedTime();

    /**
     * 获取预期配送时间
     * @return 预期配送时间，可能为null
     */
    LocalDateTime getExpectedDeliveryTime();

    /**
     * 获取实际送达时间
     * @return 实际送达时间，可能为null
     */
    LocalDateTime getDeliveredTime();

    /**
     * 获取订单当前状态
     * @return 订单状态
     */
    OrderStatus getOrderStatus();

    /**
     * 设置订单状态
     * @param status 新的订单状态
     */
    void setOrderStatus(OrderStatus status);

    /**
     * 获取订单所有者
     * @return 创建订单的用户
     */
    User getUser();

    /**
     * 获取分配的配送员
     * @return 分配的配送员，可能为null
     */
    User getAssignedUser();

    /**
     * 设置分配的配送员
     * @param user 配送员
     */
    void setAssignedUser(User user);

    /**
     * 获取当前超时状态
     * @return 超时状态
     */
    TimeoutStatus getTimeoutStatus();

    /**
     * 设置超时状态
     * @param status 新的超时状态
     */
    void setTimeoutStatus(TimeoutStatus status);

    /**
     * 检查是否已发送超时警告
     * @return 是否已发送警告
     */
    boolean isTimeoutWarningSent();

    /**
     * 设置超时警告发送状态
     * @param sent 是否已发送警告
     */
    void setTimeoutWarningSent(boolean sent);

    /**
     * 获取超时次数
     * @return 超时次数
     */
    int getTimeoutCount();

    /**
     * 设置超时次数
     * @param count 新的超时次数
     */
    void setTimeoutCount(int count);

    /**
     * 获取订单类型
     * @return 超时订单类型
     */
    TimeoutOrderType getTimeoutOrderType();

    /**
     * 获取干预时间
     * @return 平台干预时间，可能为null
     */
    LocalDateTime getInterventionTime();

    /**
     * 设置干预时间
     * @param time 平台干预时间
     */
    void setInterventionTime(LocalDateTime time);
}