package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户退款申请工单 */
@Data
@TableName("refund_request")
public class RefundRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    /** 申请时绑定的打手，待接单可空 */
    private Long playerId;
    /** 申请时订单状态快照：PAID / ASSIGNED */
    private String orderStatusSnapshot;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long operatorId;
    private String operatorRemark;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String userNickname;
    @TableField(exist = false)
    private String playerNickname;
}
