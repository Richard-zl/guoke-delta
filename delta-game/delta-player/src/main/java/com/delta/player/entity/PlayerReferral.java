package com.delta.player.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 打手与转介绍客户的绑定。一个用户同时只能有一条有效记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("player_referral")
public class PlayerReferral extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private Long userId;
    /** 库存小数，0.0500 表示 5% */
    private BigDecimal rate;
    /** ACTIVE 有效，UNBOUND 已解绑 */
    private String status;
    /** 有效绑定为 0，解绑后改为本行 id，用来保证唯一 */
    private Long activeKey;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
}
