package com.server.anki.friend.enums;

import lombok.Getter;

// ContactType.java
@Getter
public enum ContactType {
    QQ("QQ"),
    WECHAT("微信"),
    PHONE("手机号"),
    EMAIL("邮箱");

    private final String description;

    ContactType(String description) {
        this.description = description;
    }

}
