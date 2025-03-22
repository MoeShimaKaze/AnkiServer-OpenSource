package com.server.anki.mailorder.service;

import com.server.anki.timeout.service.OrderArchiveService;
import com.server.anki.mailorder.entity.MailOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 废弃订单服务
 * 为保持兼容性，委托新的OrderArchiveService处理
 */
@Service
public class AbandonedOrderService {

    private static final Logger logger = LoggerFactory.getLogger(AbandonedOrderService.class);

    @Autowired
    private OrderArchiveService orderArchiveService;

    /**
     * 将订单归档为废弃订单并删除原订单
     * 委托给OrderArchiveService处理
     */
    @Transactional
    public void archiveAndDeleteOrder(MailOrder order) {
        logger.info("委托归档订单: {}", order.getOrderNumber());
        orderArchiveService.archiveOrder(order);
    }
}