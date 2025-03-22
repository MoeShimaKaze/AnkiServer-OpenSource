package com.server.anki.shopping.exception;

/**
 * 无效操作异常
 * 当执行的操作违反业务规则或不满足当前状态的条件时抛出此异常
 */
public class InvalidOperationException extends ShoppingException {

    private static final String ERROR_CODE = "INVALID_OPERATION";

    /**
     * 创建一个无效操作异常
     * @param message 错误消息
     */
    public InvalidOperationException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 创建一个带有具体操作信息的无效操作异常
     * @param operation 操作名称
     * @param reason 失败原因
     */
    public InvalidOperationException(String operation, String reason) {
        super(String.format("无效的操作 '%s': %s", operation, reason), ERROR_CODE);
    }

    /**
     * 创建一个带有状态信息的无效操作异常
     * @param operation 操作名称
     * @param currentStatus 当前状态
     * @param requiredStatus 所需状态
     */
    public InvalidOperationException(String operation, String currentStatus,
                                     String requiredStatus) {
        super(String.format("无法执行操作 '%s'：当前状态为 %s，需要状态为 %s",
                operation, currentStatus, requiredStatus), ERROR_CODE);
    }
}