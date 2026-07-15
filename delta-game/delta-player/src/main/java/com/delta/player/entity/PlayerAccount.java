package com.delta.player.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("player_account")
public class PlayerAccount extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private String type;
    private String accountName;
    private String accountNo;
    /** 收款码图片URL（支付宝/微信） */
    private String qrcodeUrl;
    private Integer isDefault;
    @TableLogic
    private Integer deleted;
}
