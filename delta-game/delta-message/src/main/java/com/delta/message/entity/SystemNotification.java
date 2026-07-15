package com.delta.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("system_notification")
public class SystemNotification {
    /** 前端兼容：列表/详情用 type 表示 bizType */
    public String getType() { return getBizType(); }
    @TableId(type = IdType.AUTO)
    private Long id;
    private String receiverType;
    private Long receiverId;
    private String title;
    private String content;
    private String bizType;
    private Long relatedId;
    private Integer isRead;
    private LocalDateTime createdAt;
}
