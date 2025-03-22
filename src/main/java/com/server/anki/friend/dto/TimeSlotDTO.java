// TimeSlotDTO.java
package com.server.anki.friend.dto;

import com.server.anki.friend.entity.TimeSlot;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class TimeSlotDTO {
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;

    public static TimeSlotDTO fromEntity(TimeSlot timeSlot) {
        TimeSlotDTO dto = new TimeSlotDTO();
        dto.setDayOfWeek(timeSlot.getDayOfWeek());
        dto.setStartTime(timeSlot.getStartTime());
        dto.setEndTime(timeSlot.getEndTime());
        return dto;
    }

    public TimeSlot toEntity() {
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setDayOfWeek(this.dayOfWeek);
        timeSlot.setStartTime(this.startTime);
        timeSlot.setEndTime(this.endTime);
        return timeSlot;
    }
}