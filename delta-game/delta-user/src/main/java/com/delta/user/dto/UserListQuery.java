package com.delta.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户列表筛选参数
 */
@Data
public class UserListQuery {
    private String keyword;
    private Integer status;
    private String levelCode;
    private Long userId;
    private LocalDateTime createdAtStart;
    private LocalDateTime createdAtEnd;
}
