package com.delta.player.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlayerShowcaseSelfVO {
    private Boolean onWall;
    private String introVoiceUrl;
    private Integer introVoiceSeconds;
    private List<String> highlightImages = new ArrayList<>();
}
