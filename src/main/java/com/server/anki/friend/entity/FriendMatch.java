package com.server.anki.friend.entity;

// FriendMatch.java

import com.server.anki.friend.converter.StringListConverter;
import com.server.anki.friend.enums.FriendMatchStatus;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "friend_match")
@Getter
@Setter
public class FriendMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne
    @JoinColumn(name = "target_id")
    private User target;

    @Column(name = "match_score")
    private Double matchScore;

    @Convert(converter = StringListConverter.class)
    @Column(name = "match_points", columnDefinition = "json")
    private List<String> matchPoints;

    // FriendMatch.java 实体类中
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status")
    private FriendMatchStatus status;  // 使用新的枚举名称

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

