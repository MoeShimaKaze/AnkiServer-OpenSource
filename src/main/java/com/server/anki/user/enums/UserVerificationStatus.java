package com.server.anki.user.enums;

import lombok.Getter;

@Getter
public enum UserVerificationStatus {
    UNVERIFIED("未认证"),
    PENDING("待审核"),
    VERIFIED("已认证"),
    REJECTED("已拒绝");

    private final String description;

    UserVerificationStatus(String description) {
        this.description = description;
    }

}