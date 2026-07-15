package com.delta.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.user.dto.UserListQuery;
import com.delta.user.entity.User;

import java.time.LocalDateTime;

/**
 * 管理端用户列表查询条件构建（Admin / CS 共用）
 */
public final class UserQueryBuilder {

    private UserQueryBuilder() {
    }

    public static LambdaQueryWrapper<User> build(UserListQuery query) {
        UserListQuery q = query != null ? query : new UserListQuery();
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();

        if (q.getUserId() != null) {
            w.eq(User::getId, q.getUserId());
        }

        if (!isBlank(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            w.and(kw -> kw.like(User::getNickname, keyword).or().like(User::getPhone, keyword));
        }

        if (q.getStatus() != null) {
            w.eq(User::getStatus, q.getStatus());
        }

        if (!isBlank(q.getLevelCode())) {
            w.eq(User::getLevelCode, q.getLevelCode().trim());
        }

        LocalDateTime start = q.getCreatedAtStart();
        LocalDateTime end = q.getCreatedAtEnd();
        if (start != null) {
            w.ge(User::getCreatedAt, start);
        }
        if (end != null) {
            w.le(User::getCreatedAt, end);
        }

        return w.orderByDesc(User::getCreatedAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
