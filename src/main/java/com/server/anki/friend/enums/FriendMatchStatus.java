package com.server.anki.friend.enums;

import lombok.Getter;

@Getter
public enum FriendMatchStatus {
    PENDING("待处理"),
    ACCEPTED("已接受"),
    REJECTED("已拒绝");

    private final String description;

    FriendMatchStatus(String description) {
        this.description = description;
    }

}
