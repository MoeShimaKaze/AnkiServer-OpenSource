package com.server.anki.shopping.service;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.repository.PurchaseRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 代购需求超时处理服务
 * 负责处理代购需求截止时间超时的自动取消
 */
@Service
public class PurchaseRequestTimeoutService {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseRequestTimeoutService.class);

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private MessageService messageService;

    /**
     * 定时检查代购需求的截止时间，对超过截止时间的需求进行自动取消
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    @Transactional
    public void checkDeadlineTimeouts() {
        logger.info("开始检查代购需求截止时间");

        LocalDateTime now = LocalDateTime.now();
        List<PurchaseRequest> timeoutRequests = purchaseRequestRepository
                .findByStatusAndDeadlineBefore(OrderStatus.PENDING, now);

        logger.info("发现 {} 个已超过截止时间的代购需求", timeoutRequests.size());

        for (PurchaseRequest request : timeoutRequests) {
            try {
                // 更新状态为已取消
                request.setStatus(OrderStatus.CANCELLED);
                request.setUpdatedAt(now);
                purchaseRequestRepository.save(request);

                // 发送通知
                messageService.sendMessage(
                        request.getUser(),
                        String.format("您的代购需求 #%s 因超过截止时间已自动取消",
                                request.getRequestNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );

                logger.info("已自动取消超时的代购需求: {}", request.getRequestNumber());
            } catch (Exception e) {
                logger.error("处理超时代购需求 {} 时发生错误: {}",
                        request.getRequestNumber(), e.getMessage(), e);
            }
        }

        logger.info("代购需求截止时间检查完成");
    }
}