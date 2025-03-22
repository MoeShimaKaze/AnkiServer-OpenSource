// FriendController.java
package com.server.anki.friend.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.friend.controller.response.FriendMatchResponse;
import com.server.anki.friend.controller.response.FriendProfileResponse;
import com.server.anki.friend.dto.FriendMatchDTO;
import com.server.anki.friend.dto.FriendProfileDTO;
import com.server.anki.friend.dto.FriendRequestDTO;
import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.enums.MatchType;
import com.server.anki.friend.exception.ProfileNotFoundException;
import com.server.anki.friend.service.FriendService;
import com.server.anki.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friend")
public class FriendController {
    private static final Logger logger = LoggerFactory.getLogger(FriendController.class);

    @Autowired
    private FriendService friendService;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * 创建或更新个人档案
     */
    @PostMapping("/profile")
    public ResponseEntity<?> createOrUpdateProfile(
            @RequestBody FriendProfileDTO profileDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到创建/更新搭子档案请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 使用带有用户参数的 toEntity 方法
            Friend profile = profileDTO.toEntity(user);  // 传入当前用户
            Friend savedProfile = friendService.createOrUpdateProfile(user, profile);
            logger.info("用户 {} 的搭子档案已更新", user.getId());
            return ResponseEntity.ok(FriendProfileResponse.fromDTO(
                    FriendProfileDTO.fromEntity(savedProfile)
            ));
        } catch (Exception e) {
            logger.error("更新搭子档案失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取个人档案
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取搭子档案请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Friend profile = friendService.getProfile(user);
            logger.info("成功获取用户 {} 的搭子档案", user.getId());
            return ResponseEntity.ok(FriendProfileResponse.fromDTO(
                    FriendProfileDTO.fromEntity(profile)
            ));
        } catch (Exception e) {
            logger.error("获取搭子档案失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/matches/{matchType}")
    public ResponseEntity<?> findMatches(
            @PathVariable MatchType matchType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到搭子匹配请求，类型: {}, 页码: {}, 每页数量: {}", matchType, page, size);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            FriendService.PaginatedMatchResult result = friendService.findPotentialMatchesWithPagination(
                    user, matchType, page, size, sortBy, direction);

            logger.info("为用户 {} 找到第 {} 页的 {} 个潜在搭子，总计 {} 个匹配",
                    user.getId(), page, result.getMatches().size(), result.getTotalElements());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("搜索搭子匹配失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 发送联系方式交换请求
     */
    @PostMapping("/request/{targetUserId}")
    public ResponseEntity<?> sendContactRequest(
            @PathVariable Long targetUserId,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到联系方式交换请求，目标用户: {}", targetUserId);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            friendService.requestContact(user, targetUserId);
            logger.info("用户 {} 向用户 {} 发送了联系方式交换请求", user.getId(), targetUserId);
            return ResponseEntity.ok("请求已发送");
        } catch (Exception e) {
            logger.error("发送联系方式交换请求失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 处理联系方式交换请求
     */
    @PostMapping("/request/{matchId}/handle")
    public ResponseEntity<?> handleContactRequest(
            @PathVariable Long matchId,
            @RequestParam boolean accept,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到处理联系方式交换请求: matchId={}, accept={}", matchId, accept);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            friendService.handleContactRequest(user, matchId, accept);
            String result = accept ? "已接受" : "已拒绝";
            logger.info("用户 {} {} 了匹配请求 {}", user.getId(), result, matchId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("处理联系方式交换请求失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取匹配用户的联系方式
     */
    @GetMapping("/contact/{targetUserId}")
    public ResponseEntity<?> getContactInfo(
            @PathVariable Long targetUserId,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取联系方式请求，目标用户: {}", targetUserId);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String contactInfo = friendService.getContactInfo(user, targetUserId);
            logger.info("用户 {} 成功获取用户 {} 的联系方式", user.getId(), targetUserId);
            return ResponseEntity.ok(contactInfo);
        } catch (Exception e) {
            logger.error("获取联系方式失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取收到的搭子请求
     */
    @GetMapping("/requests/received")
    public ResponseEntity<?> getReceivedRequests(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取搭子请求列表请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<FriendRequestDTO> requests = friendService.getReceivedRequests(user);
            logger.info("成功获取用户 {} 的搭子请求列表，共 {} 条", user.getId(), requests.size());
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            logger.error("获取搭子请求列表失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取发送的搭子请求
     */
    @GetMapping("/requests/sent")
    public ResponseEntity<?> getSentRequests(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取已发送搭子请求列表请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<FriendRequestDTO> requests = friendService.getSentRequests(user);
            logger.info("成功获取用户 {} 的已发送搭子请求列表，共 {} 条", user.getId(), requests.size());
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            logger.error("获取已发送搭子请求列表失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取已匹配的搭子列表
     */
    @GetMapping("/matches")
    public ResponseEntity<?> getMatches(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取已匹配搭子列表请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<FriendMatchDTO> matches = friendService.getMatches(user);
            logger.info("成功获取用户 {} 的已匹配搭子列表，共 {} 条", user.getId(), matches.size());
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            logger.error("获取已匹配搭子列表失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取用户自己的搭子档案详情
     */
    @GetMapping("/my-profile")
    public ResponseEntity<?> getMyProfile(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取自己搭子档案详情请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            FriendProfileDTO profile = friendService.getMyProfile(user);
            logger.info("成功获取用户 {} 的搭子档案详情", user.getId());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            logger.error("获取搭子档案详情失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取搭子详情（包含档案和匹配信息）
     */
    @GetMapping("/detail/{userId}")
    public ResponseEntity<?> getFriendDetail(
            @PathVariable Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取用户 {} 的搭子详情请求", userId);
        User currentUser = authenticationService.getAuthenticatedUser(request, response);

        if (currentUser == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<String, Object> detailInfo = friendService.getFriendProfileWithMatchInfo(currentUser, userId);
            logger.info("成功获取用户 {} 的搭子详情", userId);
            return ResponseEntity.ok(detailInfo);
        } catch (ProfileNotFoundException e) {
            logger.error("获取搭子详情失败：档案不存在", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("搭子档案不存在");
        } catch (Exception e) {
            logger.error("获取搭子详情失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取推荐的搭子列表
     */
    @GetMapping("/matches/recommended")
    public ResponseEntity<?> getRecommendedFriends(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取推荐搭子请求，数量限制: {}", limit);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<FriendMatchDTO> recommendedDTOs = friendService.getRecommendedFriends(user, limit);
            List<FriendMatchResponse> recommendedResponses = recommendedDTOs.stream()
                    .map(FriendMatchResponse::fromDTO)
                    .collect(Collectors.toList());

            logger.info("成功获取用户 {} 的推荐搭子列表，共 {} 条", user.getId(), recommendedResponses.size());
            return ResponseEntity.ok(recommendedResponses);
        } catch (Exception e) {
            logger.error("获取推荐搭子列表失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取待处理的请求数量
     */
    @GetMapping("/requests/pending-count")
    public ResponseEntity<Integer> getPendingRequestsCount(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取待处理请求数量请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            int count = friendService.getPendingRequestsCount(user);
            logger.info("成功获取用户 {} 的待处理请求数量: {}", user.getId(), count);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            logger.error("获取待处理请求数量失败", e);
            return ResponseEntity.badRequest().body(0); // 出错时返回0
        }
    }
}