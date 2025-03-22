// FriendProfileResponse.java
package com.server.anki.friend.controller.response;

import com.server.anki.friend.dto.*;
import com.server.anki.friend.enums.ContactType;
import com.server.anki.friend.enums.MatchType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FriendProfileResponse {
    private Long id;
    private String username;
    private String university;
    private Double latitude;
    private Double longitude;
    private List<String> hobbies;
    private List<TalentDTO> talents;
    private List<GameSkillDTO> gameSkills;
    private List<String> studySubjects;
    private List<String> sports;
    private List<TravelDestinationDTO> travelDestinations;
    private List<TimeSlotDTO> availableTimes;
    private MatchType preferredMatchType;
    private ContactType contactType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FriendProfileResponse fromDTO(FriendProfileDTO dto) {
        FriendProfileResponse response = new FriendProfileResponse();
        response.setId(dto.getId());
        response.setUsername(dto.getUsername());
        response.setUniversity(dto.getUniversity());
        response.setLatitude(dto.getLatitude());
        response.setLongitude(dto.getLongitude());
        response.setHobbies(dto.getHobbies());
        response.setTalents(dto.getTalents());
        response.setGameSkills(dto.getGameSkills());
        response.setStudySubjects(dto.getStudySubjects());
        response.setSports(dto.getSports());
        response.setTravelDestinations(dto.getTravelDestinations());
        response.setAvailableTimes(dto.getAvailableTimes());
        response.setPreferredMatchType(dto.getPreferredMatchType());
        response.setContactType(dto.getContactType());
        response.setCreatedAt(dto.getCreatedAt());
        response.setUpdatedAt(dto.getUpdatedAt());
        return response;
    }
}
