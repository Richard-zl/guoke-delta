package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("order_progress")
public class OrderProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String type;
    private String operatorType;
    private Long operatorId;
    private String fromStatus;
    private String toStatus;
    private String content;
    private String images;
    private String remark;
    private LocalDateTime createdAt;
}
