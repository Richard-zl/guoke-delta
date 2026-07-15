package com.delta.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin")
public class Admin extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String role;
    private String phone;
    private Integer status;
    private Integer loginFailCount;
    private java.time.LocalDateTime lockTime;
    private java.time.LocalDateTime lastLoginAt;
    private String lastLoginIp;
    @TableLogic
    private Integer deleted;
}
