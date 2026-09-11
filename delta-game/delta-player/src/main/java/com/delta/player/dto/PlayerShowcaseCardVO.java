package com.delta.player.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlayerShowcaseCardVO {
    private Long playerId;
    private String nickname;
    private String avatar;
    private String coverUrl;
    private String tagline;
    private BigDecimal avgRating;
    private Integer completedOrders;
    private BigDecimal completeRate;
    private List<String> skillTags = new ArrayList<>();
    private Integer isOnline;
    private Integer activeOrders;
    private Integer maxConcurrent;
    private Boolean canDesignate;
    private String designateBlockReason;
}
