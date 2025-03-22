// ComprehensiveMatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.server.anki.amap.AmapService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ComprehensiveMatchStrategy implements MatchStrategy {
    private static final double MAX_DISTANCE = 5000.0; // 5公里

    @Autowired
    private AmapService amapService;

    @Autowired
    private GameMatchStrategy gameStrategy;

    @Autowired
    private HobbyMatchStrategy hobbyStrategy;

    @Autowired
    private StudyMatchStrategy studyStrategy;

    @Autowired
    private SportsMatchStrategy sportsStrategy;

    @Autowired
    private TalentMatchStrategy talentStrategy;

    @Autowired
    private TravelMatchStrategy travelStrategy;

    @Override
    public MatchResult calculateMatch(Friend profile1, Friend profile2) {
        Map<String, Double> matchDetails = new HashMap<>();

        // 游戏匹配度 (15%)
        MatchResult gameResult = gameStrategy.calculateMatch(profile1, profile2);
        double gameScore = gameResult.score();
        matchDetails.put("game", gameScore);

        // 兴趣匹配度 (15%)
        MatchResult hobbyResult = hobbyStrategy.calculateMatch(profile1, profile2);
        double hobbyScore = hobbyResult.score();
        matchDetails.put("hobby", hobbyScore);

        // 学习匹配度 (15%)
        MatchResult studyResult = studyStrategy.calculateMatch(profile1, profile2);
        double studyScore = studyResult.score();
        matchDetails.put("study", studyScore);

        // 运动匹配度 (15%)
        MatchResult sportsResult = sportsStrategy.calculateMatch(profile1, profile2);
        double sportsScore = sportsResult.score();
        matchDetails.put("sports", sportsScore);

        // 特长匹配度 (15%)
        MatchResult talentResult = talentStrategy.calculateMatch(profile1, profile2);
        double talentScore = talentResult.score();
        matchDetails.put("talent", talentScore);

        // 旅行匹配度 (15%)
        MatchResult travelResult = travelStrategy.calculateMatch(profile1, profile2);
        double travelScore = travelResult.score();
        matchDetails.put("travel", travelScore);

        // 时间和距离匹配度 (10%)
        double timeAndDistanceScore = calculateTimeAndDistanceMatch(profile1, profile2);
        matchDetails.put("timeAndDistance", timeAndDistanceScore);

        // 计算综合分数
        double totalScore = gameScore * 0.15 +
                hobbyScore * 0.15 +
                studyScore * 0.15 +
                sportsScore * 0.15 +
                talentScore * 0.15 +
                travelScore * 0.15 +
                timeAndDistanceScore * 0.10;

        // 添加详细的匹配点说明
        addMatchingDetails(matchDetails, profile1, profile2);

        return MatchResult.of(totalScore, matchDetails);
    }

    private double calculateTimeAndDistanceMatch(Friend profile1, Friend profile2) {
        // 时间匹配度 (60%)
        double timeScore = calculateTimeMatch(profile1.getAvailableTimes(), profile2.getAvailableTimes());

        // 距离分数 (40%)
        double distanceScore = calculateDistanceScore(profile1, profile2);

        return timeScore * 0.6 + distanceScore * 0.4;
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

    private void addMatchingDetails(Map<String, Double> matchDetails,
                                    Friend profile1, Friend profile2) {
        // 添加共同游戏
        List<String> commonGames = profile1.getGameSkills().stream()
                .map(GameSkill::getGameName)
                .filter(game -> profile2.getGameSkills().stream()
                        .map(GameSkill::getGameName)
                        .anyMatch(g -> g.equals(game)))
                .toList();
        matchDetails.put("commonGamesCount", (double) commonGames.size());

        // 添加共同兴趣
        List<String> commonHobbies = profile1.getHobbies().stream()
                .filter(profile2.getHobbies()::contains)
                .toList();
        matchDetails.put("commonHobbiesCount", (double) commonHobbies.size());

        // 添加共同学习科目
        List<String> commonSubjects = profile1.getStudySubjects().stream()
                .filter(profile2.getStudySubjects()::contains)
                .toList();
        matchDetails.put("commonSubjectsCount", (double) commonSubjects.size());

        // 添加共同运动项目
        List<String> commonSports = profile1.getSports().stream()
                .filter(profile2.getSports()::contains)
                .toList();
        matchDetails.put("commonSportsCount", (double) commonSports.size());

        // 添加共同特长
        List<String> commonTalents = profile1.getTalents().stream()
                .map(Talent::getTalentName)
                .filter(talent -> profile2.getTalents().stream()
                        .map(Talent::getTalentName)
                        .anyMatch(t -> t.equals(talent)))
                .toList();
        matchDetails.put("commonTalentsCount", (double) commonTalents.size());

        // 添加共同旅行目的地
        List<String> commonDestinations = profile1.getTravelDestinations().stream()
                .map(TravelDestination::getDestination)
                .filter(dest -> profile2.getTravelDestinations().stream()
                        .map(TravelDestination::getDestination)
                        .anyMatch(d -> d.equals(dest)))
                .toList();
        matchDetails.put("commonDestinationsCount", (double) commonDestinations.size());

        // 计算时间重叠率
        matchDetails.put("timeOverlapRate", calculateTimeMatch(
                profile1.getAvailableTimes(),
                profile2.getAvailableTimes()
        ));

        // 计算距离
        double distance = amapService.calculateWalkingDistance(
                profile1.getLatitude(), profile1.getLongitude(),
                profile2.getLatitude(), profile2.getLongitude()
        );
        matchDetails.put("distance", distance);
    }
}