package com.server.anki.timeout.service;

import com.server.anki.timeout.entity.TimeoutResults;
import com.server.anki.timeout.enums.TimeoutStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TimeoutWarningRecordManager {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutWarningRecordManager.class);

    private final ConcurrentHashMap<Long, TimeoutResults.TimeoutWarningRecord> warningRecords = new ConcurrentHashMap<>();

    /**
     * 获取警告记录
     */
    public TimeoutResults.TimeoutWarningRecord getWarningRecord(Long orderId) {
        return warningRecords.get(orderId);
    }

    /**
     * 保存警告记录
     */
    public void saveWarningRecord(Long orderId, TimeoutStatus status) {
        warningRecords.put(orderId, new TimeoutResults.TimeoutWarningRecord(status, LocalDateTime.now()));
    }

    /**
     * 删除警告记录
     */
    public void removeWarningRecord(Long orderId) {
        warningRecords.remove(orderId);
    }

    /**
     * 清理过期记录
     */
    public void cleanupExpiredRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        int beforeSize = warningRecords.size();

        warningRecords.entrySet().removeIf(entry ->
                entry.getValue().timestamp().isBefore(threshold));

        int removedCount = beforeSize - warningRecords.size();
        if (removedCount > 0) {
            logger.info("Cleaned up {} expired warning records", removedCount);
        }
    }
}