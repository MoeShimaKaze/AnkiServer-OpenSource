// TravelDestination.java
package com.server.anki.friend.entity;

import com.server.anki.friend.enums.TravelType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "travel_destination")
@Getter
@Setter
public class TravelDestination {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "friend_id")
    private Friend friend;

    @Column(name = "destination")
    private String destination;

    @Column(name = "province")
    private String province;

    @Column(name = "country")
    private String country;

    @Column(name = "travel_type")
    @Enumerated(EnumType.STRING)
    private TravelType travelType;

    @Column(name = "expected_season")
    private String expectedSeason;
}
