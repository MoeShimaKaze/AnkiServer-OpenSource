// TalentMatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.Talent;
import com.server.anki.friend.entity.TimeSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.server.anki.amap.AmapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TalentMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 5000.0; // 5公里

    @Autowired
    private AmapService amapService;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 特长互补匹配度 (60%)
        double talentScore = calculateTalentMatch(profile1.getTalents(), profile2.getTalents());
        matchDetails.put("talent", talentScore);

        // 时间匹配度 (20%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());
        matchDetails.put("timeMatch", timeScore);

        // 距离分数 (20%)
        double distanceScore = calculateDistanceScore(profile1, profile2);
        matchDetails.put("distance", distanceScore);

        double totalScore = talentScore * 0.6 + timeScore * 0.2 + distanceScore * 0.2;

        return MatchResult.of(totalScore, matchDetails);
    }

    private double calculateTalentMatch(List<Talent> talents1, List<Talent> talents2) {
        if (talents1.isEmpty() || talents2.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int matchCount = 0;

        for (Talent talent1 : talents1) {
            for (Talent talent2 : talents2) {
                if (talent1.getTalentName().equals(talent2.getTalentName())) {
                    matchCount++;

                    // 计算互补性分数
                    double complementScore = calculateComplementScore(talent1, talent2);

                    // 计算等级匹配分数
                    double levelScore = calculateLevelScore(talent1, talent2);

                    totalScore += (complementScore * 0.7 + levelScore * 0.3);
                }
            }
        }

        return matchCount > 0 ? totalScore / matchCount : 0.0;
    }

    private double calculateComplementScore(Talent talent1, Talent talent2) {
        // 一个想教一个想学，完美互补
        if (talent1.isCanTeach() != talent2.isCanTeach()) {
            return 1.0;
        }
        // 都想教或都想学，一般匹配
        return 0.5;
    }

    private double calculateLevelScore(Talent talent1, Talent talent2) {
        int levelDiff = Math.abs(
                talent1.getProficiency().ordinal() -
                        talent2.getProficiency().ordinal()
        );
        return Math.max(0, 1.0 - (levelDiff * 0.25));
    }

    private double calculateTimeMatch(List<TimeSlot> times1, List<TimeSlot> times2) {
        if (times1.isEmpty() || times2.isEmpty()) {
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

    private boolean isTimeSlotOverlapping(TimeSlot slot1, TimeSlot slot2) {
        if (slot1.getDayOfWeek() != slot2.getDayOfWeek()) {
            return false;
        }
        return !(slot1.getEndTime().isBefore(slot2.getStartTime()) ||
                slot2.getEndTime().isBefore(slot1.getStartTime()));
    }

    private double calculateDistanceScore(Friend profile1, Friend profile2) {
        // 使用直线距离替代步行距离
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