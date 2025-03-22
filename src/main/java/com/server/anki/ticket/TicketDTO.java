package com.server.anki.ticket;

import com.server.anki.chat.dto.ChatDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

public class TicketDTO {
    // Getters and setters
    @Setter
    @Getter
    private Long id;
    @Setter
    @Getter
    private String issue;
    @Setter
    @Getter
    private int type;  // 确保类型为 int
    @Setter
    @Getter
    private LocalDateTime createdDate;
    @Setter
    @Getter
    private LocalDateTime closedDate;
    private boolean isOpen;
    @Setter
    @Getter
    private Long userId;
    @Setter
    @Getter
    private Set<ChatDTO> chats;
    @Setter
    @Getter
    private Long closedByUserId;
    @Setter
    @Getter
    private boolean closedByAdmin;
    // 新增用于回复工单的字段
    @Setter
    @Getter
    private String message;
    // 新增字段，用于指定处理工单的管理员ID
    @Setter
    @Getter
    private Long assignedAdminId;
    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

}
