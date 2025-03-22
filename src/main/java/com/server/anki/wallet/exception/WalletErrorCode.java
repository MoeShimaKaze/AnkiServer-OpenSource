package com.server.anki.wallet.exception;

import lombok.Getter;

/**
 * 钱包错误码枚举
 */
@Getter
public enum WalletErrorCode {
  INSUFFICIENT_BALANCE(1001, "余额不足"),
  WITHDRAWAL_LIMIT_EXCEEDED(1002, "超出提现限额"),
  INVALID_WITHDRAWAL_METHOD(1003, "无效的提现方式"),
  SYSTEM_ERROR(9999, "系统错误");

  private final int code;
  private final String message;

  WalletErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

}