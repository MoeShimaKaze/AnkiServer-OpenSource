// FriendMatchResponse.java
package com.server.anki.friend.controller.response;

import com.server.anki.friend.dto.FriendMatchDTO;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 好友匹配响应对象
 * 包含匹配结果的详细信息和各维度的统计数据
 */
@Data
public class FriendMatchResponse {
    private Long id;
    private String username;
    private String university;
    private Double matchScore;
    private Map<String, Double> matchDetails;
    private Map<String, Integer> commonItems;

    /**
     * 将DTO转换为响应对象
     * 包含了空值处理和数据安全转换
     */
    public static FriendMatchResponse fromDTO(FriendMatchDTO dto) {
        FriendMatchResponse response = new FriendMatchResponse();
        response.setId(dto.getId());
        response.setUsername(dto.getProfile().getUsername());
        response.setUniversity(dto.getProfile().getUniversity());
        response.setMatchScore(dto.getMatchScore());
        response.setMatchDetails(dto.getMatchDetails());

        // 构建共同项目统计
        Map<String, Integer> commonItems = new HashMap<>();
        Map<String, Double> details = dto.getMatchDetails();

        // 安全地获取各维度的计数值
        commonItems.put("hobbies", getCountValue(details, "commonHobbiesCount"));
        commonItems.put("games", getCountValue(details, "commonGamesCount"));
        commonItems.put("sports", getCountValue(details, "commonSportsCount"));
        commonItems.put("subjects", getCountValue(details, "commonSubjectsCount"));
        commonItems.put("talents", getCountValue(details, "commonTalentsCount"));
        commonItems.put("destinations", getCountValue(details, "commonDestinationsCount"));

        response.setCommonItems(commonItems);
        return response;
    }

    /**
     * 安全地从Map中获取计数值
     * 处理可能的空值情况，提供默认值0
     */
    private static int getCountValue(Map<String, Double> details, String key) {
        if (details == null) {
            return 0;
        }
        Double value = details.get(key);
        return value != null ? value.intValue() : 0;
    }
}