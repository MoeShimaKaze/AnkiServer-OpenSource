package com.server.anki.friend.enums;

import lombok.Getter;

@Getter
public enum MatchType {
    GAME("游戏搭子", "寻找游戏好友一起组队"),
    HOBBY("兴趣搭子", "找到志同道合的兴趣伙伴"),
    STUDY("学习搭子", "结识学习伙伴互相进步"),
    SPORTS("运动搭子", "找到运动伙伴一起锻炼"),
    TALENT("特长搭子", "与特长互补的朋友一起提升"),
    TRAVEL("旅游搭子", "寻找志同道合的旅行伙伴"),
    COMPREHENSIVE("综合匹配", "全方位寻找合适的朋友");

    private final String description;
    private final String detail;

    MatchType(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

}