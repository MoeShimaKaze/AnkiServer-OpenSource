// FriendRepository.java
package com.server.anki.friend.repository;

import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.enums.MatchType;
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
public interface FriendRepository extends JpaRepository<Friend, Long> {
    // 根据用户查找档案
    Optional<Friend> findByUser(User user);

    // 根据用户ID查找档案
    Optional<Friend> findByUserId(Long userId);

    // 根据匹配类型查找潜在匹配
    @Query("SELECT f FROM Friend f WHERE " +
            "f.preferredMatchType = :matchType AND " +
            "f.user.id != :userId AND " +
            "f.university = :university")
    List<Friend> findPotentialMatchesByTypeAndUniversity(
            @Param("matchType") MatchType matchType,
            @Param("userId") Long userId,
            @Param("university") String university);

    // 查找附近的用户（基于经纬度范围）
    @Query("SELECT f FROM Friend f WHERE " +
            "f.user.id != :userId AND " +
            "f.latitude BETWEEN :minLat AND :maxLat AND " +
            "f.longitude BETWEEN :minLng AND :maxLng")
    List<Friend> findNearbyFriends(
            @Param("userId") Long userId,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    // 分页查询特定匹配类型的用户
    Page<Friend> findByPreferredMatchType(MatchType matchType, Pageable pageable);

    // 添加分页的综合查询方法
    @Query("SELECT f FROM Friend f WHERE f.user.id != :userId")
    Page<Friend> findPotentialMatches(@Param("userId") Long userId, Pageable pageable);

    // 查找共同兴趣的用户
    @Query("SELECT f FROM Friend f JOIN f.hobbies h WHERE " +
            "h IN :hobbies AND f.user.id != :userId " +
            "GROUP BY f.id HAVING COUNT(DISTINCT h) >= :minCommonHobbies")
    List<Friend> findByCommonHobbies(
            @Param("userId") Long userId,
            @Param("hobbies") List<String> hobbies,
            @Param("minCommonHobbies") int minCommonHobbies);
}