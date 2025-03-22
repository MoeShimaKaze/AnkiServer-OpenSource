// TalentDTO.java
package com.server.anki.friend.dto;

import com.server.anki.friend.entity.Talent;
import com.server.anki.friend.enums.TalentLevel;
import lombok.Data;

@Data
public class TalentDTO {
    private String talentName;
    private TalentLevel proficiency;
    private String certification;
    private boolean canTeach;

    public static TalentDTO fromEntity(Talent talent) {
        TalentDTO dto = new TalentDTO();
        dto.setTalentName(talent.getTalentName());
        dto.setProficiency(talent.getProficiency());
        dto.setCertification(talent.getCertification());
        dto.setCanTeach(talent.isCanTeach());
        return dto;
    }

    public Talent toEntity() {
        Talent talent = new Talent();
        talent.setTalentName(this.talentName);
        talent.setProficiency(this.proficiency);
        talent.setCertification(this.certification);
        talent.setCanTeach(this.canTeach);
        return talent;
    }
}
