package com.server.anki.ticket;

/**
 * 工单未找到异常
 * 当尝试访问、修改或删除不存在的工单时抛出此异常
 */
public class TicketNotFoundException extends RuntimeException {

    /**
     * 创建一个新的工单未找到异常
     * @param message 异常信息，说明具体的错误原因
     */
    public TicketNotFoundException(String message) {
        super(message);
    }

    /**
     * 创建一个新的工单未找到异常，带有原因
     * @param message 异常信息，说明具体的错误原因
     * @param cause 导致此异常的原始异常
     */
    public TicketNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 使用工单ID创建异常信息
     * @param ticketId 未找到的工单ID
     * @return 新的工单未找到异常实例
     */
    public static TicketNotFoundException forTicketId(Long ticketId) {
        return new TicketNotFoundException(String.format("无法找到ID为 %d 的工单", ticketId));
    }
}