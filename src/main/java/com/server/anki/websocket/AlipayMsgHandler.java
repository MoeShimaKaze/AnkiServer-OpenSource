package com.server.anki.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.msg.MsgHandler;
import com.server.anki.alipay.AlipayLoginService;
import com.server.anki.alipay.AlipayService;
import com.server.anki.alipay.message.AlipayMessage;
import com.server.anki.alipay.message.AlipayMessageService;
import com.server.anki.config.RedisConfig;
import com.server.anki.utils.IdempotentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝消息处理器
 * 用于处理支付宝推送的各类实时消息，包括交易状态变更、退款、转账等
 */
@Component
public class AlipayMsgHandler implements MsgHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlipayMsgHandler.class);

    // 消息类型常量
    private static final String MSG_TYPE_ORDER_CHANGED = "alipay.open.mini.order.changed";
    private static final String MSG_TYPE_ORDER_SETTLE = "alipay.open.mini.order.settle.notify";
    private static final String MSG_TYPE_FUND_TRANS = "alipay.fund.trans.order.changed";
    private static final String MSG_TYPE_AUTH_TOKEN = "alipay.system.oauth.token.changed";

    // 订单状态常量
    private static final String TRADE_STATUS_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_STATUS_CLOSED = "TRADE_CLOSED";
    private static final String TRADE_STATUS_REFUND = "TRADE_REFUND";

    // 转账状态常量
    private static final String TRANSFER_STATUS_SUCCESS = "SUCCESS";
    private static final String TRANSFER_STATUS_FAIL = "FAIL";

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private AlipayMessageService messageService;

    @Autowired
    private AlipayLoginService loginService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private IdempotentHelper idempotentHelper;

    @Override
    public void onMessage(String msgApi, String msgId, String bizContent) {
        logger.info("收到支付宝消息推送 >>>>>>>>");
        logger.info("消息API: {}", msgApi);
        logger.info("消息ID: {}", msgId);
        logger.info("消息内容: {}", bizContent);

        try {
            // 幂等性检查，避免重复处理同一消息
            String idempotentKey = "alipay:msg:" + msgId;
            if (!idempotentHelper.tryProcess(idempotentKey, 7)) {
                logger.info("消息已处理过，跳过重复处理: {}", msgId);
                return;
            }

            // 解析消息类型
            JSONObject msgContent = JSON.parseObject(bizContent);
            String msgType = msgContent.getString("msg_type");

            if (msgType == null) {
                logger.warn("消息类型为空，无法处理");
                return;
            }

            // 根据消息类型分发处理
            switch (msgType) {
                case MSG_TYPE_ORDER_CHANGED:
                    handleOrderStatusChanged(msgContent);
                    break;

                case MSG_TYPE_ORDER_SETTLE:
                    handleOrderSettle(msgContent);
                    break;

                case MSG_TYPE_FUND_TRANS:
                    handleFundTransChanged(msgContent);
                    break;

                case MSG_TYPE_AUTH_TOKEN:
                    handleAuthTokenChanged(msgContent);
                    break;

                default:
                    logger.info("未知消息类型: {}, 暂不处理", msgType);
            }

            logger.info("消息处理完成 <<<<<<<<");

            // 标记消息已处理成功
            idempotentHelper.markProcessed(idempotentKey);

        } catch (Exception e) {
            logger.error("消息处理失败: {}", e.getMessage(), e);
            // 记录处理失败以便后续可能的重试
            recordFailedMessage(msgId, bizContent, e);
            throw new RuntimeException("消息处理失败", e);
        }
    }

    /**
     * 处理订单状态变更消息
     */
    private void handleOrderStatusChanged(JSONObject msgContent) {
        try {
            JSONObject bizContent = msgContent.getJSONObject("biz_content");
            if (bizContent == null) {
                logger.warn("订单状态变更消息内容为空");
                return;
            }

            // 转换为 AlipayMessage 对象
            AlipayMessage message = new AlipayMessage();
            message.setType(MSG_TYPE_ORDER_CHANGED);
            message.setMessageId(msgContent.getString("msg_id"));

            // 解析业务内容
            AlipayMessage.MessageContent content = new AlipayMessage.MessageContent();
            content.setOrderNumber(bizContent.getString("out_trade_no"));
            content.setAlipayTradeNo(bizContent.getString("trade_no"));
            content.setTradeStatus(bizContent.getString("trade_status"));

            // 设置金额信息
            if (bizContent.getBigDecimal("total_amount") != null) {
                content.setTotalAmount(bizContent.getBigDecimal("total_amount"));
            }

            message.setContent(content);

            // 使用消息服务处理
            if (message.isPaymentSuccess()) {
                // 处理支付成功
                handlePaymentSuccess(content);
            } else if (message.isTradeClose()) {
                // 处理交易关闭
                handleTradeClose(content);
            } else if (message.isRefundSuccess()) {
                // 处理退款成功
                handleRefundSuccess(content);
            } else {
                // 使用统一订单状态查询
                messageService.handleOrderStatusChange(message);
            }

        } catch (Exception e) {
            logger.error("处理订单状态变更消息失败", e);
            throw new RuntimeException("处理订单状态变更消息失败", e);
        }
    }

    /**
     * 处理支付成功消息
     */
    private void handlePaymentSuccess(AlipayMessage.MessageContent content) {
        // 构建支付宝回调参数，与HTTP回调参数保持一致的格式
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", content.getOrderNumber());
        params.put("trade_no", content.getAlipayTradeNo());
        params.put("trade_status", TRADE_STATUS_SUCCESS);

        if (content.getTotalAmount() != null) {
            params.put("total_amount", content.getTotalAmount().toString());
        }

        if (content.getPaymentTime() != null) {
            params.put("gmt_payment", content.getPaymentTime().toString());
        }

        // 使用现有支付服务处理支付成功逻辑
        alipayService.handlePaymentSuccess(params);
        logger.info("处理支付成功消息完成: {}", content.getOrderNumber());
    }

    /**
     * 处理交易关闭消息
     */
    private void handleTradeClose(AlipayMessage.MessageContent content) {
        try {
            // 调用关闭订单的方法
            alipayService.closeUnpaidOrder(content.getOrderNumber());
            logger.info("处理交易关闭消息完成: {}", content.getOrderNumber());
        } catch (Exception e) {
            logger.error("处理交易关闭消息失败: {}", e.getMessage());
        }
    }

    /**
     * 处理退款成功消息
     */
    private void handleRefundSuccess(AlipayMessage.MessageContent content) {
        if (content.getTotalAmount() != null) {
            alipayService.handleRefundSuccess(
                    content.getOrderNumber(),
                    content.getAlipayTradeNo(),
                    content.getTotalAmount().toString()
            );
            logger.info("处理退款成功消息完成: {}", content.getOrderNumber());
        } else {
            logger.warn("退款金额为空，无法处理退款消息: {}", content.getOrderNumber());
        }
    }

    /**
     * 处理订单结算消息
     */
    private void handleOrderSettle(JSONObject msgContent) {
        try {
            JSONObject bizContent = msgContent.getJSONObject("biz_content");
            if (bizContent == null) {
                logger.warn("订单结算消息内容为空");
                return;
            }

            // 转换为 AlipayMessage 对象
            AlipayMessage message = new AlipayMessage();
            message.setType(MSG_TYPE_ORDER_SETTLE);
            message.setMessageId(msgContent.getString("msg_id"));

            // 解析结算信息
            AlipayMessage.MessageContent content = new AlipayMessage.MessageContent();
            content.setOrderNumber(bizContent.getString("out_trade_no"));

            // 解析结算信息
            JSONObject settleInfo = bizContent.getJSONObject("settle_info");
            if (settleInfo != null) {
                AlipayMessage.SettleInfo settlement = new AlipayMessage.SettleInfo();
                settlement.setSettleAmount(settleInfo.getBigDecimal("settle_amount"));
                settlement.setSettleTime(LocalDateTime.now()); // 或从消息中解析
                content.setSettleInfo(settlement);
            }

            message.setContent(content);

            // 处理结算消息
            messageService.handleOrderSettle(message);

        } catch (Exception e) {
            logger.error("处理订单结算消息失败", e);
            throw new RuntimeException("处理订单结算消息失败", e);
        }
    }

    /**
     * 处理转账状态变更消息
     * 主要用于处理提现结果的实时通知
     */
    private void handleFundTransChanged(JSONObject msgContent) {
        try {
            JSONObject bizContent = msgContent.getJSONObject("biz_content");
            if (bizContent == null) {
                logger.warn("转账状态变更消息内容为空");
                return;
            }

            // 提取转账订单信息
            String outBizNo = bizContent.getString("out_biz_no");
            String orderId = bizContent.getString("order_id");
            String status = bizContent.getString("status");

            // 检查是否是提现订单（以W开头的订单号为提现订单）
            if (outBizNo != null && outBizNo.startsWith("W")) {
                logger.info("处理提现状态变更消息: 订单号={}, 状态={}", outBizNo, status);

                // 构建回调参数
                Map<String, String> params = new HashMap<>();
                params.put("out_biz_no", outBizNo);
                params.put("order_id", orderId);
                params.put("status", status);

                if (bizContent.getString("error_code") != null) {
                    params.put("error_code", bizContent.getString("error_code"));
                }

                // 使用提现通知处理逻辑
                alipayService.verifyNotify(params); // 验证通知签名

                // 处理提现结果
                if (TRANSFER_STATUS_SUCCESS.equals(status)) {
                    // 提现成功处理
                    processWithdrawalSuccess(outBizNo, orderId);
                } else if (TRANSFER_STATUS_FAIL.equals(status)) {
                    // 提现失败处理
                    processWithdrawalFailure(outBizNo, bizContent.getString("error_code"));
                }
            } else {
                logger.info("非提现相关的转账消息，暂不处理: {}", outBizNo);
            }

        } catch (Exception e) {
            logger.error("处理转账状态变更消息失败", e);
            throw new RuntimeException("处理转账状态变更消息失败", e);
        }
    }

    /**
     * 处理提现成功
     */
    private void processWithdrawalSuccess(String outBizNo, String orderId) {
        Map<String, String> params = new HashMap<>();
        params.put("out_biz_no", outBizNo);
        params.put("order_id", orderId);
        params.put("status", TRANSFER_STATUS_SUCCESS);

        // 调用提现处理
        alipayService.handleWithdrawalSuccess(params);
        logger.info("处理提现成功消息完成: {}", outBizNo);
    }

    /**
     * 处理提现失败
     */
    private void processWithdrawalFailure(String outBizNo, String errorCode) {
        Map<String, String> params = new HashMap<>();
        params.put("out_biz_no", outBizNo);
        params.put("status", TRANSFER_STATUS_FAIL);
        params.put("error_code", errorCode);

        // 调用提现处理
        alipayService.handleWithdrawalFailure(params);
        logger.info("处理提现失败消息完成: {}", outBizNo);
    }

    /**
     * 处理授权令牌变更消息
     * 用于处理支付宝登录授权相关的消息
     */
    private void handleAuthTokenChanged(JSONObject msgContent) {
        try {
            JSONObject bizContent = msgContent.getJSONObject("biz_content");
            if (bizContent == null) {
                logger.warn("授权令牌变更消息内容为空");
                return;
            }

            // 提取授权信息
            String authAppId = bizContent.getString("auth_app_id");
            String userId = bizContent.getString("user_id");
            String appAuthToken = bizContent.getString("app_auth_token");
            String action = bizContent.getString("action");

            logger.info("收到授权令牌变更消息: 用户ID={}, 动作={}", userId, action);

            // 根据动作类型处理
            if ("AUTHORIZED".equals(action)) {
                // 处理新授权
                handleUserAuthorized(userId, appAuthToken);
            } else if ("EXPIRED".equals(action) || "CLOSED".equals(action)) {
                // 处理授权过期或关闭
                handleUserAuthExpired(userId);
            }

        } catch (Exception e) {
            logger.error("处理授权令牌变更消息失败", e);
            throw new RuntimeException("处理授权令牌变更消息失败", e);
        }
    }

    /**
     * 处理用户授权
     */
    private void handleUserAuthorized(String userId, String appAuthToken) {
        // 可以在Redis中记录用户授权状态
        String authKey = RedisConfig.getAlipayAuthTokenKey(userId);
        redisTemplate.opsForValue().set(authKey, appAuthToken, Duration.ofDays(30));

        // 通知登录服务更新用户授权信息
        loginService.handleUserReauthorized(userId, appAuthToken);

        logger.info("用户授权处理完成: {}", userId);
    }

    /**
     * 处理用户授权过期
     */
    private void handleUserAuthExpired(String userId) {
        // 清除Redis中的授权记录
        String authKey = RedisConfig.getAlipayAuthTokenKey(userId);
        redisTemplate.delete(authKey);

        // 通知登录服务用户授权已过期
        loginService.handleUserAuthExpired(userId);

        logger.info("用户授权过期处理完成: {}", userId);
    }

    /**
     * 记录处理失败的消息
     * 便于后续分析和可能的重试
     */
    private void recordFailedMessage(String msgId, String bizContent, Exception e) {
        try {
            // 存储失败消息到Redis，设置较长的过期时间
            String failedKey = "alipay:msg:failed:" + msgId;
            JSONObject failedInfo = new JSONObject();
            failedInfo.put("msgId", msgId);
            failedInfo.put("bizContent", bizContent);
            failedInfo.put("errorMessage", e.getMessage());
            failedInfo.put("timestamp", LocalDateTime.now().toString());

            redisTemplate.opsForValue().set(failedKey, failedInfo.toJSONString(), Duration.ofDays(7));

            logger.warn("已记录处理失败的消息: {}", msgId);
        } catch (Exception ex) {
            logger.error("记录失败消息时发生错误", ex);
        }
    }
}