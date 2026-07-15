package com.delta.player.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("withdraw")
public class Withdraw extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private Long accountId;
    private BigDecimal amount;
    private String status;
    private String payMethod;
    private String proofImage;
    private String rejectReason;
    private Long processedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
    private LocalDateTime lastNotifyAt;

    /** 打手昵称（非DB字段） */
    @TableField(exist = false)
    private String playerName;
    /** 打手手机号（非DB字段） */
    @TableField(exist = false)
    private String playerPhone;
    /** 收款账户类型（非DB字段） */
    @TableField(exist = false)
    private String accountType;
    /** 收款账号（非DB字段） */
    @TableField(exist = false)
    private String accountNo;
    /** 收款人姓名（非DB字段） */
    @TableField(exist = false)
    private String accountName;
    /** 收款码图片URL（非DB字段） */
    @TableField(exist = false)
    private String qrcodeUrl;
}
