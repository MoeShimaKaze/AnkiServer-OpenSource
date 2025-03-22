package com.server.anki.friend.entity;

import com.server.anki.friend.enums.TalentLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "talent")
@Getter
@Setter
public class Talent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "friend_id")
    private Friend friend;

    @Column(name = "talent_name")
    private String talentName;

    @Column(name = "proficiency")
    @Enumerated(EnumType.STRING)
    private TalentLevel proficiency;

    @Column(name = "certification")
    private String certification;

    @Column(name = "can_teach")
    private boolean canTeach;
}
