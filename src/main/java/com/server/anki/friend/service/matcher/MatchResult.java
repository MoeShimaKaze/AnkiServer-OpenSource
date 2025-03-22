package com.server.anki.friend.service.matcher;

import java.util.Map;

// 匹配结果类
public record MatchResult(
        double score,             // 匹配分数 0-1
        Map<String, Double> matchDetails  // 各维度的匹配分数
) {
    public static MatchResult of(double score, Map<String, Double> matchDetails) {
        return new MatchResult(score, matchDetails);
    }
}
