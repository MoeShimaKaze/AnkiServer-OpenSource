package com.server.anki.mailorder;

/**
 * 订单验证结果
 * 包含验证是否通过以及相关的提示信息
 * @param isValid 是否验证通过
 * @param message 验证结果消息(验证失败时提供错误信息)
 */
public record OrderValidationResult(
        boolean isValid,
        String message
) {
    /**
     * 创建验证成功的结果
     */
    public static OrderValidationResult success() {
        return new OrderValidationResult(true, "验证通过");
    }

    /**
     * 创建验证失败的结果
     */
    public static OrderValidationResult failure(String message) {
        return new OrderValidationResult(false, message);
    }

    /**
     * 获取友好的错误提示
     */
    public String getUserFriendlyMessage() {
        if (isValid) {
            return "订单信息验证通过";
        }
        return "订单信息验证失败: " + (message != null ? message : "未知错误");
    }
}