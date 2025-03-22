package com.server.anki.message.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    public void sendAlert(String message) {
        // 这里实现告警逻辑，比如：
        // 1. 发送邮件
        // 2. 发送短信
        // 3. 发送到监控系统
        // 4. 写入告警日志
        logger.error("系统告警: {}", message);
    }
}
