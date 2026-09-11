package com.delta.player.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerShowcaseDetailVO extends PlayerShowcaseCardVO {
    private String bio;
    private String introVoiceUrl;
    private Integer introVoiceSeconds;
    private List<String> highlightImages = new ArrayList<>();
}
