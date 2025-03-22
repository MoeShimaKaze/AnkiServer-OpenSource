// MatchNotFoundException.java
package com.server.anki.friend.exception;

/**
 * 匹配未找到异常
 */
public class MatchNotFoundException extends FriendException {
  private static final String ERROR_CODE = "MATCH_NOT_FOUND";

  public MatchNotFoundException(Long matchId) {
    super(ERROR_CODE, String.format("匹配记录 %d 不存在", matchId));
  }

  public MatchNotFoundException(Long userId, Long targetId) {
    super(ERROR_CODE, String.format("用户 %d 和用户 %d 之间的匹配记录不存在", userId, targetId));
  }

  public MatchNotFoundException(String message) {
    super(ERROR_CODE, message);
  }

  public MatchNotFoundException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
  }
}