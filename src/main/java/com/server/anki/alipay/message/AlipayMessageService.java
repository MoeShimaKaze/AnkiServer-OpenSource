package com.server.anki.alipay.message;

import com.server.anki.pay.payment.PaymentStatusResponse;
import com.server.anki.alipay.AlipayService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝消息服务
 * 负责处理支付宝推送的各类消息，并转换为系统内部的处理格式
 */
@Service
public class AlipayMessageService {
    private static final Logger logger = LoggerFactory.getLogger(AlipayMessageService.class);

    @Autowired
    private AlipayService alipayService;

    /**
     * 处理订单状态变更消息
     * 使用统一查询方法处理订单状态
     */
    public void handleOrderStatusChange(AlipayMessage message) {
        logger.info("处理订单状态变更消息: {}", message.getSummary());

        try {
            // 获取消息内容
            AlipayMessage.MessageContent content = message.getContent();
            if (content == null) {
                logger.error("消息内容为空");
                return;
            }

            // 使用统一查询方法查询并更新订单状态
            PaymentStatusResponse statusResponse =
                    alipayService.queryUnifiedPaymentStatus(content.getOrderNumber());

            // 根据查询结果处理特殊情况
            if (statusResponse.getStatus() == PaymentStatusResponse.Status.ERROR) {
                logger.error("订单状态查询失败: {}", statusResponse.getMessage());
                // 可以在这里添加重试逻辑或告警通知
            }

        } catch (Exception e) {
            logger.error("处理订单状态变更消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理订单状态变更消息失败", e);
        }
    }

    @NotNull
    private static Map<String, String> getStringStringMap(AlipayMessage.MessageContent content) {
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", content.getOrderNumber());
        params.put("trade_no", content.getAlipayTradeNo());
        params.put("trade_status", content.getTradeStatus());

        // 如果有金额信息，也添加到参数中
        if (content.getTotalAmount() != null) {
            params.put("total_amount", content.getTotalAmount().toString());
        }

        // 添加时间信息
        if (content.getPaymentTime() != null) {
            params.put("gmt_payment", content.getPaymentTime().toString());
        }
        return params;
    }

    /**
     * 处理订单结算消息
     */
    public void handleOrderSettle(AlipayMessage message) {
        logger.info("处理订单结算消息: {}", message.getSummary());

        try {
            AlipayMessage.MessageContent content = message.getContent();
            if (content == null || content.getSettleInfo() == null) {
                logger.error("结算消息内容为空");
                return;
            }

            AlipayMessage.SettleInfo settleInfo = content.getSettleInfo();
            alipayService.handleOrderSettle(
                    content.getOrderNumber(),
                    settleInfo.getSettleAmount().toString()
            );

        } catch (Exception e) {
            logger.error("处理订单结算消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理订单结算消息失败", e);
        }
    }
}