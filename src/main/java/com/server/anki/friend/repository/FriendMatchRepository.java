// FriendMatchRepository.java
package com.server.anki.friend.repository;

import com.server.anki.friend.entity.FriendMatch;
import com.server.anki.friend.enums.FriendMatchStatus;
import com.server.anki.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendMatchRepository extends JpaRepository<FriendMatch, Long> {
    // 检查是否已存在匹配请求
    boolean existsByRequesterAndTargetId(User requester, Long targetId);

    // 查找特定状态的匹配记录
    Optional<FriendMatch> findByRequesterAndTargetIdAndStatus(
            User requester, Long targetId, FriendMatchStatus status);

    // 获取用户收到的匹配请求
    Page<FriendMatch> findByTargetAndStatus(
            User target, FriendMatchStatus status, Pageable pageable);

    // 获取用户发送的匹配请求
    Page<FriendMatch> findByRequesterAndStatus(
            User requester, FriendMatchStatus status, Pageable pageable);

    // 统计待处理的请求数量
    Long countByTargetAndStatus(User target, FriendMatchStatus status);

    // 根据被请求次数查询热门用户（按照被匹配次数排序）
    @Query("SELECT fm.target.id AS userId, COUNT(fm) AS requestCount FROM FriendMatch fm " +
            "WHERE fm.target.id <> :currentUserId " +
            "GROUP BY fm.target.id ORDER BY requestCount DESC")
    Page<Object[]> findMostRequestedUsers(@Param("currentUserId") Long currentUserId, Pageable pageable);

    // 添加按匹配分数排序的查询方法
    @Query("SELECT fm FROM FriendMatch fm WHERE fm.requester.id = :userId OR fm.target.id = :userId")
    Page<FriendMatch> findMatchesWithScores(@Param("userId") Long userId, Pageable pageable);

    // 获取两个用户之间的匹配记录
    Optional<FriendMatch> findByRequesterAndTargetId(User requester, Long targetId);

    // 查询互相匹配成功的用户
    @Query("SELECT fm FROM FriendMatch fm WHERE " +
            "fm.status = :status AND " +
            "((fm.requester = :user AND fm.target.id = :targetId) OR " +
            "(fm.target = :user AND fm.requester.id = :targetId))")
    Optional<FriendMatch> findMutualMatch(
            @Param("user") User user,
            @Param("targetId") Long targetId,
            @Param("status") FriendMatchStatus status);
    // 新增方法
    // 获取收到的请求
    @Query("SELECT fm FROM FriendMatch fm WHERE fm.target.id = :userId")
    Page<FriendMatch> findReceivedRequests(@Param("userId") Long userId, Pageable pageable);

    // 获取发送的请求
    @Query("SELECT fm FROM FriendMatch fm WHERE fm.requester.id = :userId")
    Page<FriendMatch> findSentRequests(@Param("userId") Long userId, Pageable pageable);

    // 获取已接受的匹配
    @Query("SELECT fm FROM FriendMatch fm WHERE (fm.requester.id = :userId OR fm.target.id = :userId) AND fm.status = 'ACCEPTED'")
    List<FriendMatch> findAcceptedMatches(@Param("userId") Long userId);

}