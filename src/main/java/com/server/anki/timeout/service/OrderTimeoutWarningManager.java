package com.server.anki.timeout.service;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.fee.calculator.TimeoutFeeCalculator;
import com.server.anki.fee.model.FeeTimeoutType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 订单超时警告管理器
 * 负责处理订单超时预警相关的业务逻辑
 */
@Service
public class OrderTimeoutWarningManager {
    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutWarningManager.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    // 使用新的统一超时费用计算器
    @Autowired
    private TimeoutFeeCalculator timeoutFeeCalculator;

    @Autowired
    private TimeoutWarningRecordManager warningRecordManager;

    /**
     * 处理超时警告
     * 检查是否需要发送新警告并更新相关状态
     */
    public void handleTimeoutWarning(MailOrder order, TimeoutStatus status,
                                     com.server.anki.timeout.enums.TimeoutType oldType) {
        TimeoutResults.TimeoutWarningRecord existingWarning = warningRecordManager.getWarningRecord(order.getId());

        // 检查是否需要发送新警告
        if (shouldSendNewWarning(existingWarning, status)) {
            // 转换为新的TimeoutType并估算潜在的超时费用
            FeeTimeoutType newFeeTimeoutType = convertToNewTimeoutType(oldType);
            BigDecimal estimatedFee = timeoutFeeCalculator.estimateTimeoutFee(order, newFeeTimeoutType);

            // 发送警告通知
            sendTimeoutWarning(order, status, estimatedFee);

            // 记录警告
            warningRecordManager.saveWarningRecord(order.getId(), status);

            // 更新订单状态
            updateOrderWarningStatus(order, status);

            logger.info("已发送超时警告，订单: {}, 状态: {}, 预估费用: {}",
                    order.getOrderNumber(), status, estimatedFee);
        }
    }

    /**
     * 判断是否需要发送新警告
     */
    private boolean shouldSendNewWarning(TimeoutResults.TimeoutWarningRecord existingWarning, TimeoutStatus status) {
        return existingWarning == null ||
                existingWarning.status() != status ||
                Duration.between(existingWarning.timestamp(), LocalDateTime.now()).toMinutes() >= 30;
    }

    /**
     * 更新订单警告状态
     */
    private void updateOrderWarningStatus(MailOrder order, TimeoutStatus status) {
        order.setTimeoutStatus(status);
        order.setTimeoutWarningSent(true);
        mailOrderRepository.save(order);
    }

    /**
     * 发送超时警告
     */
    private void sendTimeoutWarning(MailOrder order, TimeoutStatus status, BigDecimal estimatedFee) {
        // TODO: 实现具体的警告通知逻辑
        // 可以集成消息推送服务等
        logger.info("正在发送超时警告，订单: {}, 状态: {}, 预估费用: {}",
                order.getOrderNumber(), status, estimatedFee);
    }

    /**
     * 将旧的TimeoutType转换为新的TimeoutType
     */
    private FeeTimeoutType convertToNewTimeoutType(com.server.anki.timeout.enums.TimeoutType oldType) {
        if (oldType == null) {
            logger.warn("收到空的超时类型，将默认使用 CONFIRMATION 类型");
            return FeeTimeoutType.CONFIRMATION;
        }

        return switch (oldType) {
            case PICKUP -> FeeTimeoutType.PICKUP;
            case DELIVERY -> FeeTimeoutType.DELIVERY;
            case CONFIRMATION -> FeeTimeoutType.CONFIRMATION;
            case INTERVENTION -> {
                logger.warn("收到干预类型的超时，将转换为 DELIVERY 类型进行处理");
                yield FeeTimeoutType.DELIVERY;
            }
        };
    }

    /**
     * 清理过期的警告记录
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void cleanupWarningRecords() {
        warningRecordManager.cleanupExpiredRecords();
    }
}