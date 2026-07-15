package com.delta.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.user.entity.UserGameInfo;
import com.delta.user.mapper.UserGameInfoMapper;
import com.delta.user.service.UserGameInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserGameInfoServiceImpl extends ServiceImpl<UserGameInfoMapper, UserGameInfo> implements UserGameInfoService {

    private static final int MAX_LIST_SIZE = 20;

    @Override
    public List<UserGameInfo> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .orderByDesc(UserGameInfo::getCreatedAt)
                .last("LIMIT " + MAX_LIST_SIZE));
    }

    @Override
    public List<UserGameInfo> listByUserAndCategory(Long userId, Long categoryId) {
        return list(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .eq(UserGameInfo::getCategoryId, categoryId)
                .orderByDesc(UserGameInfo::getCreatedAt)
                .last("LIMIT " + MAX_LIST_SIZE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long userId, String gameAccount, String contact, String remark) {
        if (userId == null || (gameAccount == null || gameAccount.isBlank()) || (contact == null || contact.isBlank())) {
            return;
        }
        String ga = gameAccount.trim();
        String co = contact.trim();
        UserGameInfo existing = getOne(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .eq(UserGameInfo::getGameAccount, ga)
                .eq(UserGameInfo::getContact, co));
        if (existing != null) {
            existing.setRemark(remark != null ? remark.trim() : null);
            existing.setCreatedAt(LocalDateTime.now());
            updateById(existing);
            return;
        }
        UserGameInfo info = new UserGameInfo();
        info.setUserId(userId);
        info.setGameAccount(ga);
        info.setContact(co);
        info.setRemark(remark != null ? remark.trim() : null);
        info.setCreatedAt(LocalDateTime.now());
        save(info);
    }

    private static final int MAX_SAVED_PER_CATEGORY = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDynamic(Long userId, Long categoryId, String savedFieldsJson, String label) {
        if (userId == null || categoryId == null || savedFieldsJson == null || savedFieldsJson.isBlank()) {
            return;
        }
        String trimLabel = label != null ? label.trim() : "";
        // 同标签去重
        UserGameInfo existing = getOne(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .eq(UserGameInfo::getCategoryId, categoryId)
                .eq(UserGameInfo::getLabel, trimLabel)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setSavedFields(savedFieldsJson);
            existing.setCreatedAt(LocalDateTime.now());
            updateById(existing);
            return;
        }
        // 检查当前分类下已保存数量，超过上限则覆盖最旧的
        List<UserGameInfo> existingList = list(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .eq(UserGameInfo::getCategoryId, categoryId)
                .orderByAsc(UserGameInfo::getCreatedAt));
        if (existingList.size() >= MAX_SAVED_PER_CATEGORY) {
            // 覆盖最旧的一条
            UserGameInfo oldest = existingList.get(0);
            oldest.setSavedFields(savedFieldsJson);
            oldest.setLabel(trimLabel);
            oldest.setCreatedAt(LocalDateTime.now());
            updateById(oldest);
            return;
        }
        UserGameInfo info = new UserGameInfo();
        info.setUserId(userId);
        info.setCategoryId(categoryId);
        info.setSavedFields(savedFieldsJson);
        info.setLabel(trimLabel);
        info.setCreatedAt(LocalDateTime.now());
        save(info);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long userId, Long id) {
        remove(new LambdaQueryWrapper<UserGameInfo>()
                .eq(UserGameInfo::getUserId, userId)
                .eq(UserGameInfo::getId, id));
    }
}
