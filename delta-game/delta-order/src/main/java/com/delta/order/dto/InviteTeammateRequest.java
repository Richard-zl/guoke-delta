package com.delta.order.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InviteTeammateRequest {
    private Long orderId;
    private Long teammatePlayerId;
    private String splitType;
    private BigDecimal splitRatio;
    private BigDecimal splitAmount;
}
