// GameSkillDTO.java
package com.server.anki.friend.dto;

import com.server.anki.friend.entity.GameSkill;
import com.server.anki.friend.enums.SkillLevel;
import lombok.Data;

@Data
public class GameSkillDTO {
    private String gameName;
    private SkillLevel skillLevel;
    private String rank;
    private String preferredPosition;

    public static GameSkillDTO fromEntity(GameSkill gameSkill) {
        GameSkillDTO dto = new GameSkillDTO();
        dto.setGameName(gameSkill.getGameName());
        dto.setSkillLevel(gameSkill.getSkillLevel());
        dto.setRank(gameSkill.getRank());
        dto.setPreferredPosition(gameSkill.getPreferredPosition());
        return dto;
    }

    public GameSkill toEntity() {
        GameSkill gameSkill = new GameSkill();
        gameSkill.setGameName(this.gameName);
        gameSkill.setSkillLevel(this.skillLevel);
        gameSkill.setRank(this.rank);
        gameSkill.setPreferredPosition(this.preferredPosition);
        return gameSkill;
    }
}