// GameMatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.GameSkill;
import com.server.anki.friend.entity.TimeSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.server.anki.amap.AmapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏搭子匹配策略
 * 主要考虑游戏技能匹配度、时间匹配度和距离三个维度
 */
@Component
public class GameMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 5000.0; // 最大匹配距离：5公里

    @Autowired
    private AmapService amapService;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 1. 计算游戏技能匹配度 (50%)
        double gameSkillScore = calculateGameSkillMatch(profile1.getGameSkills(), profile2.getGameSkills());
        matchDetails.put("gameSkill", gameSkillScore);

        // 2. 计算并记录共同游戏数量
        long commonGamesCount = calculateCommonGamesCount(profile1.getGameSkills(), profile2.getGameSkills());
        matchDetails.put("commonGamesCount", (double) commonGamesCount);

        // 3. 计算时间匹配度 (30%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());
        matchDetails.put("timeMatch", timeScore);

        // 4. 计算距离分数 (20%)
        double distanceScore = calculateDistanceScore(profile1, profile2);
        matchDetails.put("distance", distanceScore);

        // 5. 为其他匹配维度设置默认值，确保数据完整性
        matchDetails.put("commonHobbiesCount", 0.0);
        matchDetails.put("commonSportsCount", 0.0);
        matchDetails.put("commonSubjectsCount", 0.0);
        matchDetails.put("commonTalentsCount", 0.0);
        matchDetails.put("commonDestinationsCount", 0.0);

        // 6. 计算总分
        double totalScore = gameSkillScore * 0.5 + timeScore * 0.3 + distanceScore * 0.2;

        return MatchResult.of(totalScore, matchDetails);
    }

    /**
     * 计算游戏技能匹配分数
     * 考虑游戏相同度、等级差异和位置偏好
     */
    private double calculateGameSkillMatch(List<GameSkill> skills1, List<GameSkill> skills2) {
        if (skills1 == null || skills2 == null || skills1.isEmpty() || skills2.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int matchCount = 0;

        for (GameSkill skill1 : skills1) {
            for (GameSkill skill2 : skills2) {
                if (skill1.getGameName().equals(skill2.getGameName())) {
                    matchCount++;

                    // 技能等级匹配度 (60%)
                    int levelDiff = Math.abs(
                            skill1.getSkillLevel().ordinal() -
                                    skill2.getSkillLevel().ordinal()
                    );
                    double levelScore = Math.max(0, 1.0 - (levelDiff * 0.25));

                    // 位置偏好匹配度 (40%)
                    double positionScore = skill1.getPreferredPosition()
                            .equals(skill2.getPreferredPosition()) ? 1.0 : 0.5;

                    totalScore += (levelScore * 0.6 + positionScore * 0.4);
                }
            }
        }

        return matchCount > 0 ? totalScore / matchCount : 0.0;
    }

    /**
     * 计算共同游戏数量
     * 只考虑不重复的游戏名称
     */
    private long calculateCommonGamesCount(List<GameSkill> skills1, List<GameSkill> skills2) {
        if (skills1 == null || skills2 == null || skills1.isEmpty() || skills2.isEmpty()) {
            return 0;
        }

        return skills1.stream()
                .map(GameSkill::getGameName)
                .distinct()
                .filter(game -> skills2.stream()
                        .map(GameSkill::getGameName)
                        .anyMatch(g -> g.equals(game)))
                .count();
    }

    /**
     * 计算时间匹配度
     * 基于可用时间段的重叠情况
     */
    private double calculateTimeMatch(List<TimeSlot> times1, List<TimeSlot> times2) {
        if (times1 == null || times2 == null || times1.isEmpty() || times2.isEmpty()) {
            return 0.0;
        }

        int overlappingSlots = 0;
        int totalSlots = Math.min(times1.size(), times2.size());

        for (TimeSlot slot1 : times1) {
            for (TimeSlot slot2 : times2) {
                if (isTimeSlotOverlapping(slot1, slot2)) {
                    overlappingSlots++;
                    break;
                }
            }
        }

        return (double) overlappingSlots / totalSlots;
    }

    /**
     * 判断两个时间段是否重叠
     */
    private boolean isTimeSlotOverlapping(TimeSlot slot1, TimeSlot slot2) {
        if (slot1.getDayOfWeek() != slot2.getDayOfWeek()) {
            return false;
        }
        return !(slot1.getEndTime().isBefore(slot2.getStartTime()) ||
                slot2.getEndTime().isBefore(slot1.getStartTime()));
    }

    /**
     * 计算距离匹配分数
     * 使用直线距离计算，距离越近分数越高
     */
    private double calculateDistanceScore(Friend profile1, Friend profile2) {
        double distance = amapService.calculateLinearDistance(
                profile1.getLatitude(), profile1.getLongitude(),
                profile2.getLatitude(), profile2.getLongitude()
        );

        if (distance > MAX_DISTANCE) {
            return 0.0;
        }

        return 1.0 - (distance / MAX_DISTANCE);
    }
}