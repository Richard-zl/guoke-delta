package com.delta.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("subscribe_message_log")
public class SubscribeMessageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userType;
    private Long userId;
    private String templateId;
    private String data;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
}
