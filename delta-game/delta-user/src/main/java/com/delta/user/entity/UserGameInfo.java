package com.delta.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户保存的下单信息，下单时可选择复用
 */
@Data
@TableName("user_game_info")
public class UserGameInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long categoryId;
    private String gameAccount;
    private String contact;
    private String remark;
    /** 动态表单字段 JSON */
    private String savedFields;
    /** 保存标签（取首个字段值方便区分） */
    private String label;
    private LocalDateTime createdAt;
}
