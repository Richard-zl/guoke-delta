package com.delta.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("points_detail")
public class PointsDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer points;        // 变动积分（正为增加，负为减少）
    private Integer balance;       // 变动后对应账户余额
    private String type;           // ORDER_CONSUME / RECHARGE / ADMIN_ADD / ADMIN_DEDUCT / TOTAL_ADMIN_*
    private String remark;         // 备注
    private Long orderId;          // 关联订单ID
    /** 账户类型：CURRENT-当前积分 TOTAL-总积分 */
    private String accountType;
    private LocalDateTime createdAt;
}