package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private String specInfo;
    private BigDecimal amount;
    /** 购买数量 */
    private Integer quantity;
    /** 下单单价快照(含规格价) */
    private BigDecimal unitPrice;
    /** 选中的规格选项ID */
    private Long variantId;
    /** 规格名快照 */
    private String variantName;
    private BigDecimal commissionRate;
    private String gameAccount;
    private String contact;
    private String remark;
    private String extraFields;
    private Integer requiredPlayerCount;
    private Long designatedPlayerId;
    private Long playerId;

    /**
     * 第二个打手ID
     */
    private Long playerId2;

    private String status;
    private LocalDateTime payDeadline;
    private LocalDateTime assignTime;
    private LocalDateTime acceptTime;
    private LocalDateTime teammateDeadline;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime confirmTime;
    private LocalDateTime autoConfirmDeadline;
    private Integer settled;
    private BigDecimal settleAmount;
    private LocalDateTime settleTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 使用的优惠券ID
     */
    private Long userCouponId;

    /** 优惠券名称（非DB字段） */
    @TableField(exist = false)
    private String couponName;

    /** 优惠券类型（非DB字段） */
    @TableField(exist = false)
    private String couponType;

    /** 下单原价，未使用券时与 amount 相同（非DB字段） */
    @TableField(exist = false)
    private BigDecimal originalAmount;

    /** 优惠券抵扣金额（非DB字段） */
    @TableField(exist = false)
    private BigDecimal couponDiscountAmount;

    /** 打手昵称（非DB字段，列表展示用） */
    @TableField(exist = false)
    private String playerName;

    /** 第二个打手名称（非DB字段） */
    @TableField(exist = false)
    private String playerName2;

    /** 打手头像（非DB字段，列表展示用） */
    @TableField(exist = false)
    private String playerAvatar;
    /** 用户昵称（非DB字段，打手端展示用） */
    @TableField(exist = false)
    private String userNickname;
    /** 用户头像（非DB字段，打手端展示用） */
    @TableField(exist = false)
    private String userAvatar;
    /** 队友列表（非DB字段，详情展示用） */
    @TableField(exist = false)
    private List<OrderPlayer> teammates;
}