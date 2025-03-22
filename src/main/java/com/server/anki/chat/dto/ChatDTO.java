package com.server.anki.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.ticket.TicketDTO;
import com.server.anki.user.UserDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class ChatDTO {
    private Long id;
    private String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private UserDTO user;
    private TicketDTO ticket;

}
