package com.server.anki.friend.dto;

import com.server.anki.friend.enums.FriendMatchStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestDTO {
    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long targetId;
    private String targetName;
    private FriendMatchStatus status;
    private Double matchScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}