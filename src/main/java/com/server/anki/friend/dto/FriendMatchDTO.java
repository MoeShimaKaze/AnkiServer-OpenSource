package com.server.anki.friend.dto;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.FriendMatch;
import com.server.anki.friend.enums.FriendMatchStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FriendMatchDTO {
    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long targetId;
    private String targetName;
    private Double matchScore;
    private List<String> matchPoints;
    private Map<String, Double> matchDetails;
    private FriendMatchStatus status;
    private LocalDateTime createdAt;
    private FriendProfileDTO profile;

    // 从 FriendMatch 实体创建 DTO
    public static FriendMatchDTO fromEntity(FriendMatch match) {
        FriendMatchDTO dto = new FriendMatchDTO();
        dto.setId(match.getId());
        dto.setRequesterId(match.getRequester().getId());
        dto.setRequesterName(match.getRequester().getUsername());
        dto.setTargetId(match.getTarget().getId());
        dto.setTargetName(match.getTarget().getUsername());
        dto.setMatchScore(match.getMatchScore());
        dto.setMatchPoints(match.getMatchPoints());
        dto.setStatus(match.getStatus());
        dto.setCreatedAt(match.getCreatedAt());
        return dto;
    }

    // 添加新方法：从 Friend 实体创建 DTO
    public static FriendMatchDTO fromFriendEntity(Friend friend) {
        FriendMatchDTO dto = new FriendMatchDTO();
        // 设置基本信息
        dto.setId(friend.getId());
        dto.setTargetId(friend.getUser().getId());
        dto.setTargetName(friend.getUser().getUsername());

        // 设置个人资料信息
        dto.setProfile(FriendProfileDTO.fromEntity(friend));

        // 初始化其他必要字段
        dto.setMatchScore(0.0); // 初始匹配分数
        dto.setCreatedAt(friend.getCreatedAt());

        return dto;
    }

    // 添加一个方法用于更新匹配分数和详情
    public void updateMatchResult(double score, Map<String, Double> details) {
        this.matchScore = score;
        this.matchDetails = details;
    }
}