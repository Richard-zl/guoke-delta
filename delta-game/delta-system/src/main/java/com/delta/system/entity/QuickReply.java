package com.delta.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quick_reply")
public class QuickReply extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String content;
    private Integer sortOrder;
    private Integer status;
    @TableLogic
    private Integer deleted;
}
