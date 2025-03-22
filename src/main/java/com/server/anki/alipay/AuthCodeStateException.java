package com.server.anki.alipay;

import lombok.Getter;

@Getter
public class AuthCodeStateException extends RuntimeException {
    private final String status;

    public AuthCodeStateException(String message, String status) {
        super(message);
        this.status = status;
    }

}
