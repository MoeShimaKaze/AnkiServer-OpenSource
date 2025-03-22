package com.server.anki.friend.enums;

import lombok.Getter;

// TravelType.java
@Getter
public enum TravelType {
    CULTURAL("文化游", "参观历史文化景点"),
    SCENERY("风景游", "欣赏自然风光"),
    FOOD("美食游", "品尝当地美食"),
    ADVENTURE("探险游", "体验刺激冒险"),
    SHOPPING("购物游", "享受购物乐趣"),
    PHOTOGRAPHY("摄影游", "拍摄美景美食");

    private final String description;
    private final String detail;

    TravelType(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

}
