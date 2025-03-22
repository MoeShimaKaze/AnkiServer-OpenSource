package com.server.anki.fee.model;

import lombok.Getter;

@Getter
public enum TimeoutType {
    PICKUP("取件超时"),
    DELIVERY("配送超时"),
    CONFIRMATION("确认超时");

    private final String description;

    TimeoutType(String description) {
        this.description = description;
    }

}