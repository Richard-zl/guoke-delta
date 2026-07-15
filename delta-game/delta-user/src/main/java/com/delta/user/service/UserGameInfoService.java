package com.delta.user.service;

import com.delta.user.entity.UserGameInfo;

import java.util.List;

public interface UserGameInfoService {
    List<UserGameInfo> listByUserId(Long userId);

    /** 按分类查询已保存的下单信息 */
    List<UserGameInfo> listByUserAndCategory(Long userId, Long categoryId);

    void save(Long userId, String gameAccount, String contact, String remark);

    /** 保存动态表单字段 */
    void saveDynamic(Long userId, Long categoryId, String savedFieldsJson, String label);

    /** 删除 */
    void deleteById(Long userId, Long id);
}
