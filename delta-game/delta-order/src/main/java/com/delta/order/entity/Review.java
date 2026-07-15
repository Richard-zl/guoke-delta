package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long playerId;
    private Long productId;
    private Integer rating;
    private String content;
    private String images;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
