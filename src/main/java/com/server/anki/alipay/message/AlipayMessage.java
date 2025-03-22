package com.server.anki.alipay.message;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 支付宝消息实体类
 * 用于处理支付宝各类消息通知
 */
@Data
public class AlipayMessage {

    /**
     * 消息类型
     * 例如：alipay.open.mini.order.changed（订单变更）
     *      alipay.open.mini.order.settle.notify（订单结算）
     */
    @JSONField(name = "msg_type")
    private String type;

    /**
     * 消息ID，用于标识唯一消息
     */
    @JSONField(name = "msg_id")
    private String messageId;

    /**
     * 消息发送时间
     */
    @JSONField(name = "send_time")
    private LocalDateTime sendTime;

    /**
     * 应用ID
     */
    @JSONField(name = "app_id")
    private String appId;

    /**
     * 消息内容
     * 包含具体的业务数据
     */
    @JSONField(name = "biz_content")
    private MessageContent content;

    /**
     * 签名
     */
    private String sign;

    /**
     * 签名类型
     */
    @JSONField(name = "sign_type")
    private String signType;

    /**
     * 消息内容实体类
     * 封装具体的业务数据
     */
    @Data
    public static class MessageContent {
        /**
         * 商户订单号
         */
        @JSONField(name = "out_trade_no")
        private String orderNumber;

        /**
         * 支付宝交易号
         */
        @JSONField(name = "trade_no")
        private String alipayTradeNo;

        /**
         * 交易状态
         * WAIT_BUYER_PAY：交易创建，等待买家付款
         * TRADE_CLOSED：未付款交易超时关闭，或支付完成后全额退款
         * TRADE_SUCCESS：交易支付成功
         * TRADE_FINISHED：交易结束，不可退款
         */
        @JSONField(name = "trade_status")
        private String tradeStatus;

        /**
         * 订单金额
         */
        @JSONField(name = "total_amount")
        private BigDecimal totalAmount;

        /**
         * 实收金额
         */
        @JSONField(name = "receipt_amount")
        private BigDecimal receiptAmount;

        /**
         * 订单标题
         */
        @JSONField(name = "subject")
        private String subject;

        /**
         * 交易创建时间
         */
        @JSONField(name = "gmt_create")
        private LocalDateTime createTime;

        /**
         * 交易付款时间
         */
        @JSONField(name = "gmt_payment")
        private LocalDateTime paymentTime;

        /**
         * 交易结束时间
         */
        @JSONField(name = "gmt_close")
        private LocalDateTime closeTime;

        /**
         * 结算信息（用于结算通知）
         */
        @JSONField(name = "settle_info")
        private SettleInfo settleInfo;
    }

    /**
     * 结算信息实体类
     */
    @Data
    public static class SettleInfo {
        /**
         * 结算金额
         */
        @JSONField(name = "settle_amount")
        private BigDecimal settleAmount;

        /**
         * 结算时间
         */
        @JSONField(name = "settle_time")
        private LocalDateTime settleTime;

        /**
         * 结算币种
         */
        @JSONField(name = "settle_currency")
        private String settleCurrency;
    }

    /**
     * 检查消息是否为订单状态变更消息
     */
    public boolean isOrderStatusChange() {
        return "alipay.open.mini.order.changed".equals(type);
    }

    /**
     * 检查消息是否为结算通知
     */
    public boolean isSettleNotify() {
        return "alipay.open.mini.order.settle.notify".equals(type);
    }

    /**
     * 获取消息摘要信息（用于日志）
     */
    public String getSummary() {
        if (content == null) {
            return String.format("消息ID: %s, 类型: %s", messageId, type);
        }
        return String.format("消息ID: %s, 类型: %s, 订单号: %s, 状态: %s",
                messageId, type, content.orderNumber, content.tradeStatus);
    }

    /**
     * 检查消息是否已过期（超过24小时）
     */
    public boolean isExpired() {
        return sendTime.plusHours(24).isBefore(LocalDateTime.now());
    }

    /**
     * 判断是否为成功支付的消息
     */
    public boolean isPaymentSuccess() {
        return content != null && "TRADE_SUCCESS".equals(content.tradeStatus);
    }

    /**
     * 判断是否为交易关闭的消息
     */
    public boolean isTradeClose() {
        return content != null && "TRADE_CLOSED".equals(content.tradeStatus);
    }

    /**
     * 判断是否为退款成功的消息
     */
    public boolean isRefundSuccess() {
        return content != null && content.tradeStatus != null &&
                content.tradeStatus.startsWith("REFUND_SUCCESS");
    }
}