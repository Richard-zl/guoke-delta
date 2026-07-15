package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("player_replace_request")
public class PlayerReplaceRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long oldPlayerId;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long operatorId;
    private String operatorRemark;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    // ========= 瞬态展示字段 =========
    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String userNickname;
    @TableField(exist = false)
    private String playerNickname;
}
