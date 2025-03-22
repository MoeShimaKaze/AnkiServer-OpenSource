// TravelDestinationDTO.java
package com.server.anki.friend.dto;

import com.server.anki.friend.entity.TravelDestination;
import com.server.anki.friend.enums.TravelType;
import lombok.Data;

@Data
public class TravelDestinationDTO {
    private String destination;
    private String province;
    private String country;
    private TravelType travelType;
    private String expectedSeason;

    public static TravelDestinationDTO fromEntity(TravelDestination destination) {
        TravelDestinationDTO dto = new TravelDestinationDTO();
        dto.setDestination(destination.getDestination());
        dto.setProvince(destination.getProvince());
        dto.setCountry(destination.getCountry());
        dto.setTravelType(destination.getTravelType());
        dto.setExpectedSeason(destination.getExpectedSeason());
        return dto;
    }

    public TravelDestination toEntity() {
        TravelDestination destination = new TravelDestination();
        destination.setDestination(this.destination);
        destination.setProvince(this.province);
        destination.setCountry(this.country);
        destination.setTravelType(this.travelType);
        destination.setExpectedSeason(this.expectedSeason);
        return destination;
    }
}
