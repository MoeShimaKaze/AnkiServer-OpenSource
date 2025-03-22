package com.server.anki.mailorder.service;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.message.service.MessageService;
import com.server.anki.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailOrderLockService {
    private static final Logger logger = LoggerFactory.getLogger(MailOrderLockService.class);

    @Autowired
    private MailOrderRepository mailOrderRepository;

    // 添加消息服务注入
    @Autowired
    private MessageService messageService;

    @Transactional
    public void lockOrder(Long orderId, String reason) {
        MailOrder order = mailOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        order.setOrderStatus(OrderStatus.LOCKED);
        order.setLockReason(reason);
        mailOrderRepository.save(order);

        // 发送订单锁定通知给订单创建者
        messageService.sendMessage(
                order.getUser(),
                String.format("您的订单 #%s 已被锁定，原因：%s",
                        order.getOrderNumber(), reason),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        // 如果订单已分配给配送员，也发送通知给配送员
        if (order.getAssignedUser() != null) {
            messageService.sendMessage(
                    order.getAssignedUser(),
                    String.format("订单 #%s 已被锁定，原因：%s",
                            order.getOrderNumber(), reason),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );
        }

        logger.info("Order {} locked. Reason: {}", orderId, reason);
    }
}
