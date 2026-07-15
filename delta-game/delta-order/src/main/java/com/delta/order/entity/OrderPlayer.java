package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_player")
public class OrderPlayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long playerId;
    private String role;
    private String status;
    private String splitType;
    private BigDecimal splitRatio;
    private BigDecimal splitAmount;
    private Long invitedBy;
    private LocalDateTime invitedAt;
    private LocalDateTime inviteDeadline;
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;
    private BigDecimal settleAmount;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;

    // ========= 瞬态展示字段 =========
    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String avatar;
    /** 邀请人昵称（邀请列表用） */
    @TableField(exist = false)
    private String fromNickname;
    @TableField(exist = false)
    private String fromAvatar;
    /** 被邀请人昵称（邀请列表用） */
    @TableField(exist = false)
    private String toNickname;
    @TableField(exist = false)
    private String toAvatar;
    /** 订单号（邀请列表用） */
    @TableField(exist = false)
    private String orderNo;
}
