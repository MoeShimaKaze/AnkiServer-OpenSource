// StudyMatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.TimeSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.server.anki.amap.AmapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StudyMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 3000.0; // 3公里，学习搭子距离要求更近

    @Autowired
    private AmapService amapService;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 学习科目匹配度 (40%)
        double subjectScore = calculateSubjectMatch(profile1.getStudySubjects(), profile2.getStudySubjects());
        matchDetails.put("subject", subjectScore);

        // 时间匹配度 (30%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());
        matchDetails.put("timeMatch", timeScore);

        // 学校匹配分数 (15%)
        double schoolScore = calculateSchoolMatch(profile1, profile2);
        matchDetails.put("school", schoolScore);

        // 距离分数 (15%)
        double distanceScore = calculateDistanceScore(profile1, profile2);
        matchDetails.put("distance", distanceScore);

        double totalScore = subjectScore * 0.4 + timeScore * 0.3 +
                schoolScore * 0.15 + distanceScore * 0.15;

        return MatchResult.of(totalScore, matchDetails);
    }

    private double calculateSubjectMatch(List<String> subjects1, List<String> subjects2) {
        if (subjects1.isEmpty() || subjects2.isEmpty()) {
            return 0.0;
        }

        long commonSubjects = subjects1.stream()
                .filter(subjects2::contains)
                .count();

        return (double) commonSubjects / Math.max(subjects1.size(), subjects2.size());
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

    private double calculateSchoolMatch(Friend profile1, Friend profile2) {
        return profile1.getUniversity().equals(profile2.getUniversity()) ? 1.0 : 0.0;
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