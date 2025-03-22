// MatchStrategy.java
package com.server.anki.friend.service.matcher;

import com.server.anki.friend.entity.Friend;

public interface MatchStrategy {
    /**
     * 计算两个用户档案之间的匹配分数
     * @param profile1 当前用户档案
     * @param profile2 目标用户档案
     * @return 匹配结果，包含分数和匹配点
     */
    MatchResult calculateMatch(Friend profile1, Friend profile2);
}