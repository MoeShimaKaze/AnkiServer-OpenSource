package com.server.anki.chat.entity;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChatMessage {
    private Long userId;
    private Long ticketId;
    private String message;

    // Getters and setters

}
