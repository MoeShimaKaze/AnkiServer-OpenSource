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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RefundProcessorService {

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MailOrderService mailOrderService;

    // 添加消息服务注入
    @Autowired
    private MessageService messageService;

    // 添加 logger 定义
    private static final Logger logger = LoggerFactory.getLogger(RefundProcessorService.class);

    @Transactional
    public void processRefunds() {
        List<MailOrder> refundingOrders = mailOrderRepository.findByOrderStatus(OrderStatus.REFUNDING);
        for (MailOrder order : refundingOrders) {
            if (isRefundDue(order)) {
                try {
                    transactionTemplate.execute(status -> {
                        // 尝试处理退款
                        mailOrderService.processRefund(order.getOrderNumber());
                        return null;
                    });
                } catch (Exception e) {
                    logger.error("处理订单 {} 自动退款时发生错误: {}",
                            order.getOrderNumber(), e.getMessage());

                    // 退款处理失败时发送通知
                    messageService.sendMessage(
                            order.getUser(),
                            String.format("订单 #%s 自动退款处理失败，原因：%s",
                                    order.getOrderNumber(), e.getMessage()),
                            MessageType.ORDER_STATUS_UPDATED,
                            null
                    );

                    // 通知配送员退款处理失败
                    if (order.getAssignedUser() != null) {
                        messageService.sendMessage(
                                order.getAssignedUser(),
                                String.format("订单 #%s 自动退款处理失败",
                                        order.getOrderNumber()),
                                MessageType.ORDER_STATUS_UPDATED,
                                null
                        );
                    }
                }
            }
        }
    }

    private boolean isRefundDue(MailOrder order) {
        boolean isDue = order.getRefundRequestedAt()
                .plusMinutes(30).isBefore(LocalDateTime.now());

        if (isDue) {
            // 发送退款即将自动处理的通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("订单 #%s 将进行自动退款处理",
                            order.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            if (order.getAssignedUser() != null) {
                messageService.sendMessage(
                        order.getAssignedUser(),
                        String.format("订单 #%s 即将进行自动退款处理",
                                order.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }
        }

        return isDue;
    }
}