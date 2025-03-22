package com.server.anki.user.enums;

import lombok.Getter;

@Getter
public enum UserIdentity {
    STUDENT("学生"),
    MERCHANT("商家"),
    PLATFORM_STAFF("平台专人"),
    VERIFIED_USER("普通实名用户");

    private final String description;

    UserIdentity(String description) {
        this.description = description;
    }
}