package com.server.anki.friend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.server.anki.friend.converter.StringListConverter;
import com.server.anki.friend.enums.ContactType;
import com.server.anki.friend.enums.MatchType;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "friend_profile")
@Getter
@Setter
public class Friend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    // 基本信息
    @Convert(converter = StringListConverter.class)
    @Column(name = "hobbies", columnDefinition = "json")
    private List<String> hobbies;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "university")
    private String university;

    // 一对多关系
    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Talent> talents;

    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSkill> gameSkills;

    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TravelDestination> travelDestinations;

    @Convert(converter = StringListConverter.class)
    @Column(name = "study_subjects", columnDefinition = "json")
    private List<String> studySubjects;

    @Convert(converter = StringListConverter.class)
    @Column(name = "sports", columnDefinition = "json")
    private List<String> sports;

    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeSlot> availableTimes;

    // 匹配和联系方式
    @Column(name = "preferred_match_type")
    @Enumerated(EnumType.STRING)
    private MatchType preferredMatchType;

    @Column(name = "contact_type")
    @Enumerated(EnumType.STRING)
    private ContactType contactType;

    @Column(name = "contact_number")
    @JsonIgnore
    private String contactNumber;

    // 时间戳
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 数据验证方法
    public void validate() {
        validateListSize(hobbies, "兴趣爱好");
        validateListSize(talents, "特长");
        validateListSize(gameSkills, "游戏技能");
        validateListSize(sports, "运动项目");
        validateListSize(travelDestinations, "旅行目的地");
        validateListSize(studySubjects, "学习科目");
    }

    private void validateListSize(List<?> list, String fieldName) {
        if (list != null && list.size() > 5) {
            throw new IllegalArgumentException(fieldName + "不能超过" + 5 + "个");
        }
    }
}
