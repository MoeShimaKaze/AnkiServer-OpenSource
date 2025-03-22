package com.server.anki.question.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class QuestionReplyDTO {
    private Long id;

    private Long questionId;

    private Long userId;

    private String userName;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    private boolean rejected = false;

    private boolean applied;
}

