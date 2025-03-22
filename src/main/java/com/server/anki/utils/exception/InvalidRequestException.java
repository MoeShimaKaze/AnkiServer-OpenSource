package com.server.anki.utils.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * 无效请求异常
 * 当请求参数无效或不符合业务规则时抛出此异常
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数
     */
    public InvalidRequestException() {
        super("无效的请求参数");
    }

    /**
     * 带有自定义消息的构造函数
     *
     * @param message 异常消息
     */
    public InvalidRequestException(String message) {
        super(message);
    }

    /**
     * 带有原因的构造函数
     *
     * @param message 异常消息
     * @param cause 引起异常的原因
     */
    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 字段验证失败构造函数
     *
     * @param fieldName 验证失败的字段名
     * @param message 验证失败的原因
     */
    public InvalidRequestException(String fieldName, String message) {
        super(String.format("字段 '%s' 验证失败: %s", fieldName, message));
    }
}