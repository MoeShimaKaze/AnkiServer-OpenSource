package com.server.anki.marketing;

import lombok.Getter;

// SpecialDateType.java - 特殊日期类型枚举
@Getter
public enum SpecialDateType {
    HOLIDAY("节假日"),
    PROMOTION("营销活动");

    private final String description;

    SpecialDateType(String description) {
        this.description = description;
    }

}
