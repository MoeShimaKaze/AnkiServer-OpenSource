package com.server.anki.wallet;

import com.server.anki.mailorder.service.RefundProcessorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundStatusChecker {

    private final RefundProcessorService refundProcessorService;

    public RefundStatusChecker(RefundProcessorService refundProcessorService) {
        this.refundProcessorService = refundProcessorService;
    }

    @Scheduled(fixedDelay = 300000) // 每5分钟执行一次
    public void checkRefundStatus() {
        refundProcessorService.processRefunds();
    }
}