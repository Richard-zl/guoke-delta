package com.delta.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private String code;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime usedAt;
    private Long orderId;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String couponName;
    @TableField(exist = false)
    private String couponType;
    @TableField(exist = false)
    private BigDecimal discountRate;
    @TableField(exist = false)
    private BigDecimal cashAmount;
    @TableField(exist = false)
    private BigDecimal minAmount;

    /** 有效状态：UNUSED / USED / EXPIRED */
    @TableField(exist = false)
    private String effectiveStatus;
}