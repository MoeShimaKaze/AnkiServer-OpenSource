// FriendException.java
package com.server.anki.friend.exception;

import lombok.Getter;

/**
 * 搭子匹配模块通用异常类
 */
@Getter
public class FriendException extends RuntimeException {
    private final String errorCode;

    public FriendException(String message) {
        super(message);
        this.errorCode = "FRIEND_ERROR";
    }

    public FriendException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FriendException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FRIEND_ERROR";
    }

    public FriendException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}