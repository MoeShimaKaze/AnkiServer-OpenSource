// GameSkill.java
package com.server.anki.friend.entity;

import com.server.anki.friend.enums.SkillLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game_skill")
@Getter
@Setter
public class GameSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "friend_id")
    private Friend friend;

    @Column(name = "game_name")
    private String gameName;

    @Column(name = "skill_level")
    @Enumerated(EnumType.STRING)
    private SkillLevel skillLevel;

    @Column(name = "game_rank")
    private String rank;

    @Column(name = "preferred_position")
    private String preferredPosition;
}

