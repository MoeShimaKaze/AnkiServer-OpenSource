package com.server.anki.wallet.exception;

import lombok.Getter;

/**
 * 钱包异常基类
 */
@Getter
public class WalletException extends RuntimeException {
    private final WalletErrorCode errorCode;

    public WalletException(WalletErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public WalletException(WalletErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WalletException(WalletErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}