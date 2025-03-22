// InvalidProfileException.java
package com.server.anki.friend.exception;

import lombok.Getter;

import java.util.List;

/**
 * 无效档案异常
 */
@Getter
public class InvalidProfileException extends FriendException {
    private static final String ERROR_CODE = "INVALID_PROFILE";
    private final List<String> validationErrors;

    public InvalidProfileException(String message) {
        super(ERROR_CODE, message);
        this.validationErrors = List.of(message);
    }

    public InvalidProfileException(List<String> validationErrors) {
        super(ERROR_CODE, "搭子档案验证失败");
        this.validationErrors = validationErrors;
    }

    public InvalidProfileException(String message, List<String> validationErrors) {
        super(ERROR_CODE, message);
        this.validationErrors = validationErrors;
    }

}
