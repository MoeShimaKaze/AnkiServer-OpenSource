package com.server.anki.question.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.question.enums.QuestionType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 问题数据传输对象
 */
@Setter
@Getter
public class QuestionDTO {
    private Long id;

    private QuestionType questionType;

    private String description;

    private String imageUrl;  // 新增图片URL字段

    private String shortTitle;

    private String contactInfo;

    private String contactName;

    private Long userId;

    private String userName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    private QuestionStatus status;

    private Long acceptedUserId;

    private String acceptedUserName;

    private int viewCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime resolvedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime closedAt;

    private Set<QuestionReplyDTO> replies;

    // 添加回复总数字段
    private int replyCount;

}