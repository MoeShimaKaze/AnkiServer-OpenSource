package com.server.anki.friend.enums;

import lombok.Getter;

@Getter
public enum SkillLevel {
    BEGINNER("入门", "新手玩家"),
    INTERMEDIATE("进阶", "熟练玩家"),
    ADVANCED("精通", "高手玩家"),
    EXPERT("专家", "资深玩家");

    private final String description;
    private final String detail;

    SkillLevel(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

}
