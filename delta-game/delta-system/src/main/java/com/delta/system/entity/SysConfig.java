package com.delta.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configName;
    private String configValue;
    private String valueType;
    private String configGroup;
    private String remark;
}
