package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_relay_request")
public class OrderRelayRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long originalPlayerId;
    private String splitType;
    private BigDecimal splitAmount;
    private String reason;
    private String status;
    private Long newPlayerId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private BigDecimal orderAmount;
    @TableField(exist = false)
    private String originalPlayerName;
    @TableField(exist = false)
    private String newPlayerName;
}
