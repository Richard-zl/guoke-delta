package com.delta.player.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("player")
public class Player extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String nickname;
    private String avatar;
    private String realName;
    private String phone;
    private String skillTags;
    private String gameLevel;
    private String proofImages;
    private String serviceTypes;
    private BigDecimal avgRating;
    private Integer orderCount;
    private BigDecimal completeRate;
    private String status;
    private String rejectReason;
    /** 打手入驻押金支付单号（金额以当时配置为准） */
    private String depositPaymentNo;
    private LocalDateTime frozenUntil;
    private LocalDateTime lastOnlineAt;
    private Integer isOnline;
    @TableLogic
    private Integer deleted;

    /** 余额（非DB字段） */
    @TableField(exist = false)
    private BigDecimal balance;
    /** 完成订单数（非DB字段） */
    @TableField(exist = false)
    private Integer completedOrders;
    /** 进行中订单数（非DB字段） */
    @TableField(exist = false)
    private Integer activeOrders;
}
