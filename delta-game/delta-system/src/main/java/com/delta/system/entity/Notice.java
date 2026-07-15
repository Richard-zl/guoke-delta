package com.delta.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notice")
public class Notice extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String type;
    private Integer status;
    private Integer sortOrder;
    /** 是否首页弹窗展示: 1-是 0-否 */
    private Integer popupDisplay;
    @TableLogic
    private Integer deleted;
}
