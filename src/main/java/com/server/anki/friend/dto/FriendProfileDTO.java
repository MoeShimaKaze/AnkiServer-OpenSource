package com.server.anki.friend.dto;

import com.server.anki.friend.entity.*;
import com.server.anki.friend.enums.ContactType;
import com.server.anki.friend.enums.MatchType;
import com.server.anki.user.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友档案数据传输对象
 * 用于前后端数据交换，包含所有必要的档案信息
 */
@Data
public class FriendProfileDTO {
    // 基本信息
    private Long id;
    private Long userId;
    private String username;

    // 位置和学校信息
    private Double latitude;
    private Double longitude;
    private String university;

    // 兴趣和技能相关列表
    private List<String> hobbies;
    private List<TalentDTO> talents;
    private List<GameSkillDTO> gameSkills;
    private List<String> studySubjects;
    private List<String> sports;
    private List<TravelDestinationDTO> travelDestinations;
    private List<TimeSlotDTO> availableTimes;

    // 匹配和联系方式
    private MatchType preferredMatchType;
    private ContactType contactType;
    private String contactNumber;

    // 时间戳
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 将实体对象转换为DTO
     * @param friend 好友档案实体
     * @return 转换后的DTO对象
     */
    public static FriendProfileDTO fromEntity(Friend friend) {
        FriendProfileDTO dto = new FriendProfileDTO();

        // 设置基本信息
        dto.setId(friend.getId());
        dto.setUserId(friend.getUser().getId());
        dto.setUsername(friend.getUser().getUsername());

        // 设置位置和学校信息
        dto.setLatitude(friend.getLatitude());
        dto.setLongitude(friend.getLongitude());
        dto.setUniversity(friend.getUniversity());

        // 设置列表字段，确保非空
        dto.setHobbies(friend.getHobbies() != null ? friend.getHobbies() : new ArrayList<>());
        dto.setStudySubjects(friend.getStudySubjects() != null ? friend.getStudySubjects() : new ArrayList<>());
        dto.setSports(friend.getSports() != null ? friend.getSports() : new ArrayList<>());

        // 转换复杂对象列表
        dto.setTalents(friend.getTalents().stream()
                .map(TalentDTO::fromEntity)
                .collect(Collectors.toList()));
        dto.setGameSkills(friend.getGameSkills().stream()
                .map(GameSkillDTO::fromEntity)
                .collect(Collectors.toList()));
        dto.setTravelDestinations(friend.getTravelDestinations().stream()
                .map(TravelDestinationDTO::fromEntity)
                .collect(Collectors.toList()));
        dto.setAvailableTimes(friend.getAvailableTimes().stream()
                .map(TimeSlotDTO::fromEntity)
                .collect(Collectors.toList()));

        // 设置匹配和联系方式
        dto.setPreferredMatchType(friend.getPreferredMatchType());
        dto.setContactType(friend.getContactType());
        dto.setContactNumber(friend.getContactNumber());

        // 设置时间戳
        dto.setCreatedAt(friend.getCreatedAt());
        dto.setUpdatedAt(friend.getUpdatedAt());

        return dto;
    }

    /**
     * 将DTO转换为实体对象
     * @param user 当前用户信息，用于建立实体关联
     * @return 转换后的实体对象
     */
// FriendProfileDTO.java
    public Friend toEntity(User user) {
        Friend friend = new Friend();

        // 设置基本信息
        friend.setUser(user);
        friend.setLatitude(this.latitude);
        friend.setLongitude(this.longitude);
        friend.setUniversity(this.university);
        friend.setPreferredMatchType(this.preferredMatchType);
        friend.setContactType(this.contactType);
        friend.setContactNumber(this.contactNumber);

        // 设置列表字段
        friend.setHobbies(this.hobbies != null ? this.hobbies : new ArrayList<>());
        friend.setStudySubjects(this.studySubjects != null ? this.studySubjects : new ArrayList<>());
        friend.setSports(this.sports != null ? this.sports : new ArrayList<>());

        // 处理游戏技能
        List<GameSkill> gameSkillList = new ArrayList<>();
        if (this.gameSkills != null) {
            for (GameSkillDTO skillDTO : this.gameSkills) {
                GameSkill skill = skillDTO.toEntity();
                skill.setFriend(friend);
                gameSkillList.add(skill);
            }
        }
        friend.setGameSkills(gameSkillList);

        // 处理特长
        List<Talent> talentList = new ArrayList<>();
        if (this.talents != null) {
            for (TalentDTO talentDTO : this.talents) {
                Talent talent = talentDTO.toEntity();
                talent.setFriend(friend);
                talentList.add(talent);
            }
        }
        friend.setTalents(talentList);

        // 处理旅行目的地
        List<TravelDestination> destinationList = new ArrayList<>();
        if (this.travelDestinations != null) {
            for (TravelDestinationDTO destDTO : this.travelDestinations) {
                TravelDestination destination = destDTO.toEntity();
                destination.setFriend(friend);
                destinationList.add(destination);
            }
        }
        friend.setTravelDestinations(destinationList);

        // 处理可用时间
        List<TimeSlot> timeSlotList = new ArrayList<>();
        if (this.availableTimes != null) {
            for (TimeSlotDTO timeDTO : this.availableTimes) {
                TimeSlot timeSlot = timeDTO.toEntity();
                timeSlot.setFriend(friend);
                timeSlotList.add(timeSlot);
            }
        }
        friend.setAvailableTimes(timeSlotList);

        // 设置时间戳
        friend.setCreatedAt(LocalDateTime.now());
        friend.setUpdatedAt(LocalDateTime.now());

        return friend;
    }
}