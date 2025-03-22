package com.server.anki.friend.enums;

import lombok.Getter;

@Getter
public enum TalentLevel {
    BEGINNER("入门", "刚开始学习"),
    INTERMEDIATE("进阶", "有一定基础"),
    ADVANCED("精通", "较为专业"),
    PROFESSIONAL("专业", "可以教授他人");

    private final String description;
    private final String detail;

    TalentLevel(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

}
