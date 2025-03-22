package com.server.anki.wallet.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class WithdrawalRequest {
    // Getters and setters
    private BigDecimal amount;
    private String withdrawalMethod; // e.g., "WECHAT", "ALIPAY"
    private String accountInfo; // 支付宝账户ID，将使用用户绑定的账户
    private String verificationCode; // 邮箱验证码
}