package com.server.anki.timeout.entity;

import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.enums.TimeoutType;
import java.time.LocalDateTime;

/**
 * 超时相关的结果记录类文件
 */
public class TimeoutResults {
    /**
     * 超时检查结果记录类
     */
    public record TimeoutCheckResult(
            TimeoutStatus status,
            TimeoutType type,
            long overtimeMinutes
    ) {
        public boolean isTimeout() {
            return status != TimeoutStatus.NORMAL;
        }

        public boolean isWarningOnly() {
            return switch (status) {
                case PICKUP_TIMEOUT_WARNING,
                     DELIVERY_TIMEOUT_WARNING,
                     CONFIRMATION_TIMEOUT_WARNING -> true;
                default -> false;
            };
        }
    }

    /**
     * 超时警告记录类
     */
    public record TimeoutWarningRecord(
            TimeoutStatus status,
            LocalDateTime timestamp
    ) {
        public boolean isExpired(LocalDateTime threshold) {
            return timestamp.isBefore(threshold);
        }

        public long getMinutesSinceWarning() {
            return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
        }
    }

    /**
     * 警告发送结果记录类
     */
    public record WarningDeliveryResult(
            boolean success,
            String message,
            LocalDateTime sentTime
    ) {
        // 移除static修饰符，改为工厂方法模式
        public WarningDeliveryResult withSuccess() {
            return new WarningDeliveryResult(true, "警告发送成功", LocalDateTime.now());
        }

        public WarningDeliveryResult withFailure(String errorMessage) {
            return new WarningDeliveryResult(false, errorMessage, LocalDateTime.now());
        }
    }
}