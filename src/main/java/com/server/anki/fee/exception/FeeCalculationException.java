package com.server.anki.fee.exception;

public class FeeCalculationException extends RuntimeException {
    // 原有构造函数（两个参数）
    public FeeCalculationException(String message, Throwable cause) {
        super(message, cause);
    }

    // 新增构造函数（一个参数）
    public FeeCalculationException(String message) {
        super(message);
    }
}