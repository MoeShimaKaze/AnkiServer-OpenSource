// ProfileNotFoundException.java
package com.server.anki.friend.exception;

/**
 * 档案未找到异常
 */
public class ProfileNotFoundException extends FriendException {
    private static final String ERROR_CODE = "PROFILE_NOT_FOUND";

    public ProfileNotFoundException(Long userId) {
        super(ERROR_CODE, String.format("用户ID %d 的搭子档案不存在", userId));
    }

    public ProfileNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    public ProfileNotFoundException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}