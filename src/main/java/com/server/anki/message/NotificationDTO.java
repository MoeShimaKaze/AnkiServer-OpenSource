package com.server.anki.message;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class NotificationDTO {
    // getters and setters
    private Long userId;
    private String content;
    private String type;
    private Long ticketId; // 添加 ticketId 字段

    public NotificationDTO(Long userId, String content, String type, Long ticketId) {
        this.userId = userId;
        this.content = content;
        this.type = type;
        this.ticketId = ticketId;
    }

}
