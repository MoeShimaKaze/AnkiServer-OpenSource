// TravelMatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.TimeSlot;
import com.server.anki.friend.entity.TravelDestination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.server.anki.amap.AmapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TravelMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 10000.0; // 10公里，旅行搭子可以距离远一些

    @Autowired
    private AmapService amapService;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 目的地匹配度 (50%)
        double destinationScore = calculateDestinationMatch(
                profile1.getTravelDestinations(),
                profile2.getTravelDestinations()
        );
        matchDetails.put("destination", destinationScore);

        // 时间匹配度 (30%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());
        matchDetails.put("timeMatch", timeScore);

        // 距离分数 (20%)
        double distanceScore = calculateDistanceScore(profile1, profile2);
        matchDetails.put("distance", distanceScore);

        double totalScore = destinationScore * 0.5 + timeScore * 0.3 + distanceScore * 0.2;

        return MatchResult.of(totalScore, matchDetails);
    }

    private double calculateDestinationMatch(
            List<TravelDestination> destinations1,
            List<TravelDestination> destinations2) {
        if (destinations1.isEmpty() || destinations2.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int matchCount = 0;

        for (TravelDestination dest1 : destinations1) {
            for (TravelDestination dest2 : destinations2) {
                double matchScore = calculateSingleDestinationMatch(dest1, dest2);
                if (matchScore > 0) {
                    totalScore += matchScore;
                    matchCount++;
                }
            }
        }

        return matchCount > 0 ? totalScore / matchCount : 0.0;
    }

    private double calculateSingleDestinationMatch(
            TravelDestination dest1,
            TravelDestination dest2) {
        // 目的地完全匹配
        if (dest1.getDestination().equals(dest2.getDestination())) {
            double score = 1.0;

            // 旅行类型匹配 (权重0.4)
            if (dest1.getTravelType() == dest2.getTravelType()) {
                score = score * 0.6 + 0.4;
            }

            // 期望季节匹配 (权重0.2)
            if (dest1.getExpectedSeason().equals(dest2.getExpectedSeason())) {
                score = score * 0.8 + 0.2;
            }

            return score;
        }

        // 同省份但不同目的地
        if (dest1.getProvince().equals(dest2.getProvince())) {
            return 0.5;
        }

        // 同国家但不同省份
        if (dest1.getCountry().equals(dest2.getCountry())) {
            return 0.3;
        }

        return 0.0;
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