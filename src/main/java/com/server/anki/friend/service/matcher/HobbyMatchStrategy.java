// HobbyMatchStrategy.java
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
public class HobbyMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 5000.0; // 5公里

    @Autowired
    private AmapService amapService;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 兴趣匹配度 (60%)
        double hobbyScore = calculateHobbyMatch(profile1.getHobbies(), profile2.getHobbies());
        matchDetails.put("hobby", hobbyScore);

        // 时间匹配度 (20%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());
        matchDetails.put("timeMatch", timeScore);

        // 距离分数 (20%)
        double distanceScore = calculateDistanceScore(profile1, profile2);
        matchDetails.put("distance", distanceScore);

        double totalScore = hobbyScore * 0.6 + timeScore * 0.2 + distanceScore * 0.2;

        return MatchResult.of(totalScore, matchDetails);
    }

    private double calculateHobbyMatch(List<String> hobbies1, List<String> hobbies2) {
        if (hobbies1.isEmpty() || hobbies2.isEmpty()) {
            return 0.0;
        }

        long commonHobbies = hobbies1.stream()
                .filter(hobbies2::contains)
                .count();

        return (double) commonHobbies / Math.max(hobbies1.size(), hobbies2.size());
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