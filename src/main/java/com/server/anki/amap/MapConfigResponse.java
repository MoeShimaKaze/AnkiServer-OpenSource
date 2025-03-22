package com.server.anki.amap;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MapConfigResponse {
    private String key;

    public MapConfigResponse(String key) {
        this.key = key;
    }

}
