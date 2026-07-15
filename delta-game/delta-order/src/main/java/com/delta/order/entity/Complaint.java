package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("complaint")
public class Complaint extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long playerId;
    private String type;
    private String content;
    private String images;
    private String expectedResult;
    private String status;
    private String result;
    private BigDecimal refundAmount;
    private String resultReason;
    private String playerPenalty;
    private Long operatorId;
    private LocalDateTime resolvedAt;
    private String appealReason;
    private String appealImages;
    private String appealResult;
    private Long appealOperatorId;
    private LocalDateTime appealResolvedAt;
    /** 客服查看时间（未读红点：未查看的待处理投诉） */
    private LocalDateTime csReadAt;
}
