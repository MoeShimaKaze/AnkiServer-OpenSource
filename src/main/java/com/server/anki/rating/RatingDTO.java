package com.server.anki.rating;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RatingDTO {
    private Long id;
    private Long raterId;
    private String raterName;
    private Long ratedUserId;
    private String ratedUserName;
    private String comment;
    private int score;
    private LocalDateTime ratingDate;
    private RatingType ratingType;
    private OrderType orderType;
    private UUID orderNumber;

    // 用于前端显示的辅助字段
    private String ratingTypeDescription;
    private String orderTypeDescription;
}