package com.server.anki.pay.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentStatusResponse {
    public enum Status {
        SUCCESS,    // 查询成功
        CREATING,   // 订单创建中
        ERROR      // 发生错误
    }

    private Status status;
    private String tradeStatus;  // 支付宝返回的交易状态
    private String message;      // 提示信息或错误信息
}
