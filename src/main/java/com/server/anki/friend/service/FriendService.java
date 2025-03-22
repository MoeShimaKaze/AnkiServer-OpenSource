package com.server.anki.friend.service;

import com.server.anki.friend.dto.FriendMatchDTO;
import com.server.anki.friend.dto.FriendProfileDTO;
import com.server.anki.friend.dto.FriendRequestDTO;
import com.server.anki.friend.entity.Friend;
import com.server.anki.friend.entity.FriendMatch;
import com.server.anki.friend.enums.FriendMatchStatus;
import com.server.anki.friend.enums.MatchType;
import com.server.anki.friend.exception.FriendException;
import com.server.anki.friend.exception.InvalidProfileException;
import com.server.anki.friend.exception.MatchNotFoundException;
import com.server.anki.friend.exception.ProfileNotFoundException;
import com.server.anki.friend.repository.FriendMatchRepository;
import com.server.anki.friend.repository.FriendRepository;
import com.server.anki.friend.service.matcher.*;
import com.server.anki.friend.service.validator.FriendProfileValidator;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.user.User;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FriendService {
    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);

    // 定义默认分页参数
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private FriendMatchRepository friendMatchRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private FriendProfileValidator validator;

    private final Map<MatchType, MatchStrategy> matchStrategies;

    // 构造函数，注入所有匹配策略
    public FriendService(
            GameMatchStrategy gameStrategy,
            HobbyMatchStrategy hobbyStrategy,
            StudyMatchStrategy studyStrategy,
            SportsMatchStrategy sportsStrategy,
            TalentMatchStrategy talentStrategy,
            TravelMatchStrategy travelStrategy,
            ComprehensiveMatchStrategy comprehensiveStrategy) {

        this.matchStrategies = Map.of(
                MatchType.GAME, gameStrategy,
                MatchType.HOBBY, hobbyStrategy,
                MatchType.STUDY, studyStrategy,
                MatchType.SPORTS, sportsStrategy,
                MatchType.TALENT, talentStrategy,
                MatchType.TRAVEL, travelStrategy,
                MatchType.COMPREHENSIVE, comprehensiveStrategy
        );
    }

    /**
     * 获取用户的搭子档案
     * 如果档案不存在，抛出ProfileNotFoundException异常
     *
     * @param user 需要查询档案的用户
     * @return 用户的搭子档案
     * @throws ProfileNotFoundException 当用户档案不存在时抛出
     */
    public Friend getProfile(User user) {
        logger.info("正在获取用户 {} 的搭子档案", user.getId());

        return friendRepository.findByUser(user)
                .orElseThrow(() -> {
                    logger.warn("用户 {} 的搭子档案不存在", user.getId());
                    return new ProfileNotFoundException(user.getId());
                });
    }

    /**
     * 根据用户ID获取搭子档案
     *
     * @param userId 用户ID
     * @return 用户的搭子档案
     * @throws ProfileNotFoundException 当用户档案不存在时抛出
     */
    public Friend getProfileById(Long userId) {
        logger.info("正在通过ID获取搭子档案: {}", userId);

        return friendRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    logger.warn("ID为 {} 的用户搭子档案不存在", userId);
                    return new ProfileNotFoundException(userId);
                });
    }

    /**
     * 创建或更新搭子档案
     *
     * @param user 用户
     * @param profile 档案信息
     * @return 保存后的档案
     * @throws InvalidProfileException 当档案验证失败时抛出
     */
    @Transactional
    public Friend createOrUpdateProfile(User user, Friend profile) {
        logger.info("开始处理用户 {} 的档案更新请求", user.getId());
        logger.debug("档案详情: {}", profile);

        // 首先查找是否存在现有档案
        Optional<Friend> existingProfile = friendRepository.findByUser(user);

        Friend profileToSave;
        if (existingProfile.isPresent()) {
            // 如果存在，更新现有档案
            Friend existing = existingProfile.get();
            // 更新基本信息
            existing.setLatitude(profile.getLatitude());
            existing.setLongitude(profile.getLongitude());
            existing.setUniversity(profile.getUniversity());
            existing.setPreferredMatchType(profile.getPreferredMatchType());
            existing.setContactType(profile.getContactType());
            existing.setContactNumber(profile.getContactNumber());

            // 更新列表字段
            existing.setHobbies(profile.getHobbies());
            existing.setStudySubjects(profile.getStudySubjects());
            existing.setSports(profile.getSports());

            // 更新特长
            existing.getTalents().clear();
            profile.getTalents().forEach(talent -> {
                talent.setFriend(existing);
                existing.getTalents().add(talent);
            });

            // 更新游戏技能
            existing.getGameSkills().clear();
            profile.getGameSkills().forEach(skill -> {
                skill.setFriend(existing);
                existing.getGameSkills().add(skill);
            });

            // 更新旅行目的地
            existing.getTravelDestinations().clear();
            profile.getTravelDestinations().forEach(dest -> {
                dest.setFriend(existing);
                existing.getTravelDestinations().add(dest);
            });

            // 更新可用时间
            existing.getAvailableTimes().clear();
            profile.getAvailableTimes().forEach(time -> {
                time.setFriend(existing);
                existing.getAvailableTimes().add(time);
            });

            // 更新时间戳
            existing.setUpdatedAt(LocalDateTime.now());

            profileToSave = existing;
            logger.info("更新现有档案，档案ID: {}", existing.getId());
        } else {
            // 如果不存在，创建新档案
            profile.setUser(user);
            profile.setCreatedAt(LocalDateTime.now());
            profile.setUpdatedAt(LocalDateTime.now());
            profileToSave = profile;
            logger.info("创建新档案");
        }

        // 验证档案
        List<String> errors = validator.validate(profileToSave);
        if (!errors.isEmpty()) {
            logger.error("档案验证失败，错误信息: {}", errors);
            throw new InvalidProfileException(errors);
        }

        Friend savedProfile = friendRepository.save(profileToSave);
        logger.info("档案保存成功，ID: {}", savedProfile.getId());
        return savedProfile;
    }

    /**
     * 带完整分页参数的潜在匹配搜索
     */
    public List<FriendMatchDTO> findPotentialMatches(User currentUser, MatchType matchType,
                                                     int page, int size, String sortField, String sortDirection) {
        logger.info("开始为用户 {} 查找 {} 类型的潜在搭子，页码：{}", currentUser.getId(), matchType, page);

        Friend userProfile = friendRepository.findByUser(currentUser)
                .orElseThrow(() -> new ProfileNotFoundException(currentUser.getId()));

        MatchStrategy strategy = matchStrategies.get(matchType);
        if (strategy == null) {
            throw new FriendException("不支持的匹配类型: " + matchType);
        }

        Pageable pageable = createPageable(page, size, sortField, sortDirection);
        Page<Friend> potentialMatchesPage = findPotentialMatchCandidates(userProfile, matchType, pageable);

        return calculateAndSortMatches(userProfile, potentialMatchesPage.getContent(), strategy);
    }

    /**
     * 查找潜在匹配并保存匹配分数
     */
    public PaginatedMatchResult findPotentialMatchesWithPagination(User currentUser,
                                                                   MatchType matchType, int page, int size, String sortField, String sortDirection) {
        Friend userProfile = friendRepository.findByUser(currentUser)
                .orElseThrow(() -> new ProfileNotFoundException(currentUser.getId()));

        MatchStrategy strategy = matchStrategies.get(matchType);
        if (strategy == null) {
            throw new FriendException("不支持的匹配类型: " + matchType);
        }

        // 根据已存储的匹配分数查询
        Page<FriendMatch> matchesPage;
        if ("matchScore".equals(sortField)) {
            // 如果要按匹配分数排序，使用FriendMatch表查询
            Sort.Direction direction = Sort.Direction.fromString(sortDirection);
            Sort sort = Sort.by(direction, "matchScore");
            // 直接创建新的PageRequest对象，而不是修改现有的对象
            PageRequest sortedPageable = PageRequest.of(page, size, sort);

            matchesPage = friendMatchRepository.findMatchesWithScores(
                    currentUser.getId(),
                    sortedPageable
            );
        } else {
            // 使用传统方式查询
            Pageable pageable = createPageable(page, size, sortField, sortDirection);
            Page<Friend> friendsPage = findPotentialMatchCandidates(userProfile, matchType, pageable);

            // 计算并保存匹配分数
            List<FriendMatchDTO> dtos = calculateAndSaveMatches(userProfile, friendsPage.getContent(), strategy);

            // 转换为分页结果
            return PaginatedMatchResult.from(friendsPage, dtos);
        }

        // 转换FriendMatch为FriendMatchDTO
        List<FriendMatchDTO> dtos = matchesPage.getContent().stream()
                .map(match -> {
                    // 获取目标用户的Profile
                    Friend targetProfile = friendRepository.findByUserId(match.getTarget().getId())
                            .orElse(null);
                    if (targetProfile == null) {
                        return null;
                    }

                    // 创建DTO
                    FriendMatchDTO dto = FriendMatchDTO.fromFriendEntity(targetProfile);
                    dto.setMatchScore(match.getMatchScore());
                    dto.setMatchDetails(convertMatchPointsToDetails(match.getMatchPoints()));
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 创建分页结果
        PaginatedMatchResult result = new PaginatedMatchResult();
        result.setMatches(dtos);
        result.setCurrentPage(matchesPage.getNumber());
        result.setTotalPages(matchesPage.getTotalPages());
        result.setTotalElements(matchesPage.getTotalElements());
        result.setHasNext(matchesPage.hasNext());
        result.setHasPrevious(matchesPage.hasPrevious());
        return result;
    }

    /**
     * 计算并保存匹配分数到数据库
     */
    private List<FriendMatchDTO> calculateAndSaveMatches(
            Friend userProfile,
            List<Friend> candidates,
            MatchStrategy strategy) {

        return candidates.stream()
                .map(candidate -> {
                    // 计算匹配结果
                    MatchResult result = strategy.calculateMatch(userProfile, candidate);

                    // 创建或更新FriendMatch记录
                    FriendMatch match = friendMatchRepository
                            .findByRequesterAndTargetId(userProfile.getUser(), candidate.getUser().getId())
                            .orElse(new FriendMatch());

                    match.setRequester(userProfile.getUser());
                    match.setTarget(candidate.getUser());
                    match.setMatchScore(result.score());
                    match.setMatchPoints(convertDetailsToMatchPoints(result.matchDetails()));

                    if (match.getStatus() == null) {
                        match.setStatus(FriendMatchStatus.PENDING);
                    }

                    if (match.getCreatedAt() == null) {
                        match.setCreatedAt(LocalDateTime.now());
                    }
                    match.setUpdatedAt(LocalDateTime.now());

                    // 保存到数据库
                    friendMatchRepository.save(match);

                    // 创建DTO
                    FriendMatchDTO dto = FriendMatchDTO.fromFriendEntity(candidate);
                    dto.setMatchScore(result.score());
                    dto.setMatchDetails(result.matchDetails());
                    return dto;
                })
                .filter(dto -> dto.getMatchScore() >= getMinimumMatchScore(strategy))
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .collect(Collectors.toList());
    }

    // 辅助方法：将Map<String, Double>转换为List<String>
    private List<String> convertDetailsToMatchPoints(Map<String, Double> details) {
        if (details == null) {
            return new ArrayList<>();
        }
        return details.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.toList());
    }

    // 辅助方法：将List<String>转回Map<String, Double>
    private Map<String, Double> convertMatchPointsToDetails(List<String> matchPoints) {
        if (matchPoints == null) {
            return new HashMap<>();
        }
        return matchPoints.stream()
                .map(point -> point.split(":", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> {
                            try {
                                return Double.parseDouble(parts[1]);
                            } catch (NumberFormatException e) {
                                return 0.0;
                            }
                        }
                ));
    }

    /**
     * 发送联系方式交换请求
     */
    @Transactional
    public void requestContact(User requester, Long targetId) {
        logger.info("用户 {} 请求与用户 {} 交换联系方式", requester.getId(), targetId);

        if (friendMatchRepository.existsByRequesterAndTargetId(requester, targetId)) {
            throw new FriendException("已经向该用户发送过请求");
        }

        FriendMatch match = new FriendMatch();
        match.setRequester(requester);
        match.setTarget(new User());
        match.getTarget().setId(targetId);
        match.setStatus(FriendMatchStatus.PENDING);
        match.setCreatedAt(LocalDateTime.now());
        match.setUpdatedAt(LocalDateTime.now());

        friendMatchRepository.save(match);

        messageService.sendMessage(
                match.getTarget(),
                String.format("用户 %s 想要与您交换联系方式", requester.getUsername()),
                MessageType.QUESTION_REPLIED,
                null
        );

        logger.info("联系方式交换请求已发送");
    }

    /**
     * 处理联系方式交换请求
     */
    @Transactional
    public void handleContactRequest(User user, Long matchId, boolean accept) {
        logger.info("用户 {} {} 匹配请求 {}", user.getId(), accept ? "接受" : "拒绝", matchId);

        FriendMatch match = friendMatchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        if (!match.getTarget().getId().equals(user.getId())) {
            throw new FriendException("无权处理该请求");
        }

        match.setStatus(accept ? FriendMatchStatus.ACCEPTED : FriendMatchStatus.REJECTED);
        match.setUpdatedAt(LocalDateTime.now());
        friendMatchRepository.save(match);

        String message = String.format(
                "用户 %s %s了您的联系方式交换请求",
                user.getUsername(),
                accept ? "接受" : "拒绝"
        );

        messageService.sendMessage(
                match.getRequester(),
                message,
                MessageType.QUESTION_REPLIED,
                null
        );

        logger.info("联系方式交换请求处理完成");
    }

    /**
     * 获取联系方式
     */
    public String getContactInfo(User requester, Long targetId) {
        logger.info("用户 {} 请求查看用户 {} 的联系方式", requester.getId(), targetId);

        FriendMatch match = friendMatchRepository
                .findByRequesterAndTargetIdAndStatus(requester, targetId, FriendMatchStatus.ACCEPTED)
                .orElseThrow(() -> new FriendException("未获得查看权限"));

        Friend targetProfile = friendRepository.findByUserId(targetId)
                .orElseThrow(() -> new ProfileNotFoundException(targetId));

        return String.format("%s: %s",
                targetProfile.getContactType().getDescription(),
                targetProfile.getContactNumber()
        );
    }

    /**
     * 检查用户是否已经创建了搭子档案
     */
    public boolean hasProfile(User user) {
        logger.debug("检查用户 {} 是否已创建搭子档案", user.getId());
        return friendRepository.findByUser(user).isPresent();
    }

    /**
     * 批量获取用户档案
     */
    public List<Friend> getProfilesByUserIds(List<Long> userIds) {
        logger.info("批量获取用户档案，用户数量: {}", userIds.size());
        return friendRepository.findAllById(userIds);
    }

    // 私有辅助方法
    private Page<Friend> findPotentialMatchCandidates(Friend userProfile, MatchType matchType,
                                                      Pageable pageable) {
        if (matchType == MatchType.COMPREHENSIVE) {
            return friendRepository.findPotentialMatches(userProfile.getUser().getId(), pageable);
        }
        return friendRepository.findByPreferredMatchType(matchType, pageable);
    }

    private Pageable createPageable(int page, int size, String sortField, String sortDirection) {
        page = Math.max(0, page);
        size = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        sortField = StringUtils.hasText(sortField) ? sortField : DEFAULT_SORT_FIELD;

        Sort sort = Sort.by(
                Sort.Direction.fromString(sortDirection.toUpperCase()),
                sortField
        );

        return PageRequest.of(page, size, sort);
    }

    private List<FriendMatchDTO> calculateAndSortMatches(
            Friend userProfile,
            List<Friend> candidates,
            MatchStrategy strategy) {

        return candidates.stream()
                .map(candidate -> {
                    MatchResult result = strategy.calculateMatch(userProfile, candidate);
                    return createMatchDTO(candidate, result);
                })
                .filter(dto -> dto.getMatchScore() >= getMinimumMatchScore(strategy))
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .toList();
    }

    private FriendMatchDTO createMatchDTO(Friend profile, MatchResult result) {
        FriendMatchDTO dto = FriendMatchDTO.fromFriendEntity(profile);
        dto.setMatchScore(result.score());
        dto.setMatchDetails(result.matchDetails());
        return dto;
    }

    private double getMinimumMatchScore(MatchStrategy strategy) {
        if (strategy instanceof GameMatchStrategy) return 0.4;
        if (strategy instanceof StudyMatchStrategy) return 0.3;
        if (strategy instanceof SportsMatchStrategy) return 0.3;
        if (strategy instanceof TalentMatchStrategy) return 0.3;
        if (strategy instanceof TravelMatchStrategy) return 0.3;
        if (strategy instanceof HobbyMatchStrategy) return 0.25;
        return 0.2; // ComprehensiveMatchStrategy
    }

    /**
     * 分页结果包装类
     */
    @Data
    public static class PaginatedMatchResult {
        private List<FriendMatchDTO> matches;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
        private boolean hasPrevious;

        public static PaginatedMatchResult from(Page<Friend> page, List<FriendMatchDTO> matches) {
            PaginatedMatchResult result = new PaginatedMatchResult();
            result.setMatches(matches);
            result.setCurrentPage(page.getNumber());
            result.setTotalPages(page.getTotalPages());
            result.setTotalElements(page.getTotalElements());
            result.setHasNext(page.hasNext());
            result.setHasPrevious(page.hasPrevious());
            return result;
        }
    }

    /**
     * 获取用户收到的请求列表
     * @param user 当前用户
     * @return 收到的联系方式交换请求列表
     */
    public List<FriendRequestDTO> getReceivedRequests(User user) {
        logger.info("获取用户 {} 收到的搭子请求", user.getId());

        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FriendMatch> requestsPage = friendMatchRepository.findReceivedRequests(user.getId(), pageable);

        return requestsPage.getContent().stream()
                .map(match -> {
                    FriendRequestDTO dto = new FriendRequestDTO();
                    dto.setId(match.getId());
                    dto.setRequesterId(match.getRequester().getId());
                    dto.setRequesterName(match.getRequester().getUsername());
                    dto.setTargetId(match.getTarget().getId());
                    dto.setTargetName(match.getTarget().getUsername());
                    dto.setStatus(match.getStatus());
                    dto.setCreatedAt(match.getCreatedAt());
                    dto.setUpdatedAt(match.getUpdatedAt());
                    dto.setMatchScore(match.getMatchScore());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户发送的请求列表
     * @param user 当前用户
     * @return 发送的联系方式交换请求列表
     */
    public List<FriendRequestDTO> getSentRequests(User user) {
        logger.info("获取用户 {} 发送的搭子请求", user.getId());

        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FriendMatch> requestsPage = friendMatchRepository.findSentRequests(user.getId(), pageable);

        return requestsPage.getContent().stream()
                .map(match -> {
                    FriendRequestDTO dto = new FriendRequestDTO();
                    dto.setId(match.getId());
                    dto.setRequesterId(match.getRequester().getId());
                    dto.setRequesterName(match.getRequester().getUsername());
                    dto.setTargetId(match.getTarget().getId());
                    dto.setTargetName(match.getTarget().getUsername());
                    dto.setStatus(match.getStatus());
                    dto.setCreatedAt(match.getCreatedAt());
                    dto.setUpdatedAt(match.getUpdatedAt());
                    dto.setMatchScore(match.getMatchScore());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户已匹配的搭子列表
     * @param user 当前用户
     * @return 已匹配的搭子列表
     */
    public List<FriendMatchDTO> getMatches(User user) {
        logger.info("获取用户 {} 的已匹配搭子", user.getId());

        List<FriendMatch> matches = friendMatchRepository.findAcceptedMatches(user.getId());
        List<FriendMatchDTO> result = new ArrayList<>();

        for (FriendMatch match : matches) {
            // 确定搭子的用户ID (不是当前用户的那一方)
            Long friendId = match.getRequester().getId().equals(user.getId())
                    ? match.getTarget().getId()
                    : match.getRequester().getId();

            // 获取搭子的档案
            Friend friendProfile;
            try {
                friendProfile = getProfileById(friendId);
            } catch (ProfileNotFoundException e) {
                logger.warn("跳过缺少档案的搭子: {}", friendId);
                continue;
            }

            // 创建DTO
            FriendMatchDTO dto = FriendMatchDTO.fromFriendEntity(friendProfile);
            dto.setMatchScore(match.getMatchScore());

            // 如果有匹配点信息，转换为匹配详情
            if (match.getMatchPoints() != null && !match.getMatchPoints().isEmpty()) {
                Map<String, Double> details = convertMatchPointsToDetails(match.getMatchPoints());
                dto.setMatchDetails(details);
            }

            result.add(dto);
        }

        return result;
    }

    /**
     * 获取用户自己的搭子档案详情
     * @param user 当前用户
     * @return 用户的搭子档案详情
     */
    public FriendProfileDTO getMyProfile(User user) {
        logger.info("获取用户 {} 的完整搭子档案", user.getId());

        Friend profile = getProfile(user);
        return FriendProfileDTO.fromEntity(profile);
    }

    /**
     * 获取搭子档案并包含匹配信息
     */
    public Map<String, Object> getFriendProfileWithMatchInfo(User currentUser, Long friendId) {
        // 获取好友档案
        Friend friendProfile = getProfileById(friendId);
        FriendProfileDTO profileDTO = FriendProfileDTO.fromEntity(friendProfile);

        // 创建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("profile", profileDTO);

        // 查找匹配关系
        Optional<FriendMatch> matchOpt = friendMatchRepository.findByRequesterAndTargetId(currentUser, friendId);
        if (matchOpt.isEmpty()) {
            // 检查反向关系
            matchOpt = friendMatchRepository.findByRequesterAndTargetId(friendProfile.getUser(), currentUser.getId());
        }

        // 填充匹配信息
        if (matchOpt.isPresent()) {
            FriendMatch match = matchOpt.get();
            result.put("matchId", match.getId());
            result.put("matchScore", match.getMatchScore());
            result.put("matchStatus", match.getStatus());
            result.put("isRequestSent", match.getRequester().getId().equals(currentUser.getId()));
            result.put("isRequestReceived", match.getTarget().getId().equals(currentUser.getId()));

            if (match.getMatchPoints() != null && !match.getMatchPoints().isEmpty()) {
                Map<String, Double> details = convertMatchPointsToDetails(match.getMatchPoints());
                result.put("matchDetails", details);

                // 计算共同特征数量
                Map<String, Integer> commonItems = new HashMap<>();
                commonItems.put("hobbies", getIntValue(details, "commonHobbiesCount"));
                commonItems.put("games", getIntValue(details, "commonGamesCount"));
                commonItems.put("sports", getIntValue(details, "commonSportsCount"));
                commonItems.put("subjects", getIntValue(details, "commonSubjectsCount"));
                commonItems.put("talents", getIntValue(details, "commonTalentsCount"));
                commonItems.put("destinations", getIntValue(details, "commonDestinationsCount"));

                result.put("commonItems", commonItems);
            }
        } else {
            // 计算潜在匹配分数（如果当前用户有档案）
            try {
                Friend currentUserProfile = getProfile(currentUser);
                MatchStrategy strategy = matchStrategies.get(MatchType.COMPREHENSIVE);
                MatchResult matchResult = strategy.calculateMatch(currentUserProfile, friendProfile);

                result.put("matchScore", matchResult.score());
                result.put("matchDetails", matchResult.matchDetails());
                result.put("matchStatus", null);
                result.put("isRequestSent", false);
                result.put("isRequestReceived", false);

                // 计算共同特征数量
                Map<String, Integer> commonItems = new HashMap<>();
                Map<String, Double> details = matchResult.matchDetails();
                commonItems.put("hobbies", getIntValue(details, "commonHobbiesCount"));
                commonItems.put("games", getIntValue(details, "commonGamesCount"));
                commonItems.put("sports", getIntValue(details, "commonSportsCount"));
                commonItems.put("subjects", getIntValue(details, "commonSubjectsCount"));
                commonItems.put("talents", getIntValue(details, "commonTalentsCount"));
                commonItems.put("destinations", getIntValue(details, "commonDestinationsCount"));

                result.put("commonItems", commonItems);
            } catch (ProfileNotFoundException e) {
                // 当前用户可能没有创建档案
                logger.warn("当前用户 {} 没有创建搭子档案，无法计算匹配度", currentUser.getId());
            }
        }

        return result;
    }

    /**
     * 获取推荐的搭子列表，基于被匹配次数
     * @param currentUser 当前用户
     * @param limit 返回结果数量限制
     * @return 推荐的搭子列表
     */
    public List<FriendMatchDTO> getRecommendedFriends(User currentUser, int limit) {
        logger.info("获取推荐搭子，用户ID: {}, 数量限制: {}", currentUser.getId(), limit);

        Pageable pageable = PageRequest.of(0, limit);
        Page<Object[]> popularUsers = friendMatchRepository.findMostRequestedUsers(currentUser.getId(), pageable);

        List<FriendMatchDTO> recommendedMatches = new ArrayList<>();
        for (Object[] result : popularUsers.getContent()) {
            Long userId = (Long) result[0];

            try {
                // 获取用户档案
                Friend friendProfile = getProfileById(userId);

                // 从用户档案创建DTO
                FriendMatchDTO dto = FriendMatchDTO.fromFriendEntity(friendProfile);

                // 尝试获取现有匹配信息
                Optional<FriendMatch> matchOpt = friendMatchRepository.findByRequesterAndTargetId(currentUser, userId);
                if (matchOpt.isEmpty()) {
                    matchOpt = friendMatchRepository.findByRequesterAndTargetId(friendProfile.getUser(), currentUser.getId());
                }

                if (matchOpt.isPresent()) {
                    // 使用现有匹配记录
                    FriendMatch match = matchOpt.get();
                    dto.setMatchScore(match.getMatchScore());
                    dto.setMatchDetails(convertMatchPointsToDetails(match.getMatchPoints()));
                } else {
                    // 计算新的匹配分数
                    try {
                        Friend currentUserProfile = getProfile(currentUser);
                        MatchStrategy strategy = matchStrategies.get(MatchType.COMPREHENSIVE);
                        MatchResult matchResult = strategy.calculateMatch(currentUserProfile, friendProfile);

                        dto.setMatchScore(matchResult.score());
                        dto.setMatchDetails(matchResult.matchDetails());
                    } catch (ProfileNotFoundException e) {
                        // 如果当前用户没有档案，设置默认匹配分数
                        dto.setMatchScore(0.5); // 默认中等匹配度
                    }
                }

                recommendedMatches.add(dto);
            } catch (ProfileNotFoundException e) {
                // 如果用户档案不存在，跳过
                logger.warn("跳过推荐：用户 {} 没有档案", userId);
            }
        }

        return recommendedMatches;
    }

    private int getIntValue(Map<String, Double> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return 0;
        }
        Double value = map.get(key);
        return value != null ? value.intValue() : 0;
    }

    /**
     * 获取用户待处理的请求数量
     * @param user 当前用户
     * @return 待处理请求数量
     */
    public int getPendingRequestsCount(User user) {
        logger.info("计算用户 {} 的待处理请求数量", user.getId());
        return friendMatchRepository.countByTargetAndStatus(user, FriendMatchStatus.PENDING).intValue();
    }
}