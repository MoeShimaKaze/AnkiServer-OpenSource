package com.server.anki.shopping.exception;

import lombok.Getter;

/**
 * 商城模块通用异常类
 * 作为商城模块所有异常的基类，提供基础的异常处理功能
 */
@Getter
public class ShoppingException extends RuntimeException {

    // 错误代码
    private final String errorCode;

    // 是否需要记录日志
    private final boolean needLog;

    /**
     * 创建一个带错误代码的异常
     * @param message 错误消息
     * @param errorCode 错误代码
     */
    public ShoppingException(String message, String errorCode) {
        this(message, errorCode, true);
    }

    /**
     * 创建一个带错误代码和日志标记的异常
     * @param message 错误消息
     * @param errorCode 错误代码
     * @param needLog 是否需要记录日志
     */
    public ShoppingException(String message, String errorCode, boolean needLog) {
        super(message);
        this.errorCode = errorCode;
        this.needLog = needLog;
    }

    /**
     * 创建一个带错误代码和原始异常的异常
     * @param message 错误消息
     * @param errorCode 错误代码
     * @param cause 原始异常
     */
    public ShoppingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.needLog = true;
    }
}