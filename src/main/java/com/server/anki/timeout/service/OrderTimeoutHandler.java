package com.server.anki.timeout.service;

import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.TimeoutType;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.event.TimeoutEvent;
import com.server.anki.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单超时处理服务
 * 负责处理订单的各种超时情况,包括取件超时、配送超时等
 */
@Service
public class OrderTimeoutHandler {
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutHandler.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    // 使用新的统一超时费用计算器
    @Autowired
    private TimeoutFeeCalculator timeoutFeeCalculator;

    @Autowired
    private WalletService walletService;

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private OrderTimeoutWarningManager warningManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 处理超时检查结果
     */
    @Transactional
    public void handleTimeoutResult(MailOrder order, TimeoutResults.TimeoutCheckResult result) {
        if (result.status() == TimeoutStatus.NORMAL) {
            return;
        }

        try {
            switch (result.status()) {
                case PICKUP_TIMEOUT -> handlePickupTimeout(order);
                case DELIVERY_TIMEOUT -> handleDeliveryTimeout(order);
                case PICKUP_TIMEOUT_WARNING, DELIVERY_TIMEOUT_WARNING, CONFIRMATION_TIMEOUT_WARNING ->
                        warningManager.handleTimeoutWarning(order, result.status(), result.type());
                default -> logger.debug("当前状态无需处理: {}", result.status());
            }

            // 发布超时事件以更新统计
            if (result.isTimeout()) {
                Long userId = order.getAssignedUser() != null ? order.getAssignedUser().getId() : null;
                eventPublisher.publishEvent(new TimeoutEvent(this, result.type().name(), userId));
            }
        } catch (Exception e) {
            logger.error("处理订单 {} 的超时状态时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 处理取件超时
     * 根据不同的配送类型和超时次数执行不同的处理逻辑
     */
    @Transactional
    protected void handlePickupTimeout(MailOrder order) {
        logger.info("处理订单 {} 的取件超时", order.getOrderNumber());
        try {
            if (order.getDeliveryService() == DeliveryService.STANDARD) {
                // STANDARD：仅当超时次数≥3时扣罚金
                if (order.getTimeoutCount() >= 3 && order.getAssignedUser() != null) {
                    // 使用新的统一费用计算器计算超时费用
                    BigDecimal timeoutFee = timeoutFeeCalculator.calculateTimeoutFee(
                            order, TimeoutType.PICKUP);
                    if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
                        processTimeoutFee(order, timeoutFee, "取件超时罚金");
                    }
                }
            } else {
                // EXPRESS订单直接处理罚金
                if (order.getAssignedUser() != null) {
                    BigDecimal timeoutFee = timeoutFeeCalculator.calculateTimeoutFee(
                            order, TimeoutType.PICKUP);
                    if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
                        processTimeoutFee(order, timeoutFee, "取件超时罚金");
                    }
                }
            }
            // 调用重置订单方法
            mailOrderService.resetOrderForReassignment(order);

            // 判断归档条件：STANDARD≥10次，EXPRESS≥3次
            int archiveThreshold = order.getDeliveryService() == DeliveryService.STANDARD ? 10 : 3;
            if (order.getTimeoutCount() >= archiveThreshold) {
                mailOrderService.archiveOrder(order);
                return;
            }

            logger.info("订单 {} 取件超时处理完成", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("处理订单 {} 取件超时错误: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("取件超时处理失败", e);
        }
    }

    /**
     * 处理配送超时
     * 计算并处理超时费用,EXPRESS订单需要平台介入
     */
    @Transactional
    protected void handleDeliveryTimeout(MailOrder order) {
        logger.info("处理订单 {} 的配送超时", order.getOrderNumber());
        BigDecimal timeoutFee = timeoutFeeCalculator.calculateTimeoutFee(
                order, TimeoutType.DELIVERY);
        if (timeoutFee.compareTo(BigDecimal.ZERO) > 0) {
            processTimeoutFee(order, timeoutFee, "配送超时罚金");
        }
        if (order.getDeliveryService() == DeliveryService.EXPRESS) {
            updateToIntervention(order);
        }
        // STANDARD订单配送超时仅扣罚金，保留原状态
    }

    /**
     * 处理超时费用
     * 扣除配送员账户余额并增加平台收入
     */
    private void processTimeoutFee(MailOrder order, BigDecimal timeoutFee, String reason) {
        try {
            String detailedReason = reason + " - 订单号: " + order.getOrderNumber();
            walletService.addPendingFunds(
                    order.getAssignedUser(),
                    timeoutFee.negate(),
                    detailedReason
            );
            order.setPlatformIncome(order.getPlatformIncome() + timeoutFee.doubleValue());
            mailOrderRepository.save(order);
            logger.info("订单 {} 的超时费用 {} 处理消息已发送",
                    order.getOrderNumber(), timeoutFee);
        } catch (Exception e) {
            logger.error("处理订单 {} 的超时费用时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 更新订单为平台介入状态
     */
    @Transactional
    protected void updateToIntervention(MailOrder order) {
        logger.info("正在将订单 {} 更新为平台介入状态", order.getOrderNumber());
        try {
            order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
            order.setInterventionTime(LocalDateTime.now());
            mailOrderRepository.save(order);
            logger.info("订单 {} 已成功更新为平台介入状态", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("将订单 {} 更新为平台介入状态时发生错误: {}",
                    order.getOrderNumber(), e.getMessage(), e);
            throw e;
        }
    }
}