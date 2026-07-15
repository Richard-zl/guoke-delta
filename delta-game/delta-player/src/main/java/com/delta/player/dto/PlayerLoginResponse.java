package com.delta.player.dto;

import lombok.Data;

@Data
public class PlayerLoginResponse {
    private String token;
    private Long playerId;
    private String nickname;
    private String avatar;
}