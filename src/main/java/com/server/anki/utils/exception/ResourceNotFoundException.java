package com.server.anki.utils.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * 资源未找到异常
 * 当请求的资源在系统中不存在时抛出此异常
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数
     */
    public ResourceNotFoundException() {
        super("请求的资源不存在");
    }

    /**
     * 带有自定义消息的构造函数
     *
     * @param message 异常消息
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * 带有原因的构造函数
     *
     * @param message 异常消息
     * @param cause 引起异常的原因
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 资源类型和标识符构造函数
     *
     * @param resourceType 资源类型，如"用户"、"订单"等
     * @param id 资源的唯一标识符
     */
    public ResourceNotFoundException(String resourceType, Object id) {
        super(String.format("%s不存在，ID: %s", resourceType, id));
    }
}