package com.server.anki.shopping.exception;

import lombok.Getter;

/**
 * 商家验证重定向异常
 * 用于处理商家验证流程中的重定向
 */
@Getter
public class RedirectToMerchantVerificationException extends Exception {
    private final Long userId;

    public RedirectToMerchantVerificationException(String message) {
        super(message);
        this.userId = null;
    }

    public RedirectToMerchantVerificationException(String message, Long userId) {
        super(message);
        this.userId = userId;
    }

}