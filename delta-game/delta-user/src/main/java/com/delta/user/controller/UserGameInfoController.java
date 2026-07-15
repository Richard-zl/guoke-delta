package com.delta.user.controller;

import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.user.entity.UserGameInfo;
import com.delta.user.service.UserGameInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户保存的下单信息：支持旧版固定字段 + 新版动态字段
 */
@RestController
@RequestMapping("/user/game-info")
@RequiredArgsConstructor
public class UserGameInfoController {
    private final UserGameInfoService userGameInfoService;

    /** 旧接口：全量列表 */
    @GetMapping("/list")
    public R<List<UserGameInfo>> list() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(userGameInfoService.listByUserId(userId));
    }

    /** 新接口：按分类查询 */
    @GetMapping("/list/{categoryId}")
    public R<List<UserGameInfo>> listByCategory(@PathVariable Long categoryId) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(userGameInfoService.listByUserAndCategory(userId, categoryId));
    }

    /** 旧接口保留：保存固定字段 */
    @PostMapping
    public R<Void> save(@RequestBody SaveGameInfoRequest request) {
        Long userId = SecurityUtils.getUserId();
        userGameInfoService.save(
                userId,
                request != null ? request.getGameAccount() : null,
                request != null ? request.getContact() : null,
                request != null ? request.getRemark() : null);
        return R.ok();
    }

    /** 新接口：保存动态字段 */
    @PostMapping("/dynamic")
    public R<Void> saveDynamic(@RequestBody SaveDynamicRequest request) {
        Long userId = SecurityUtils.getUserId();
        // 将 Map 转为 JSON 字符串存储
        String json = null;
        if (request.getSavedFields() != null) {
            try {
                json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.getSavedFields());
            } catch (Exception e) {
                return R.fail("字段格式错误");
            }
        }
        userGameInfoService.saveDynamic(userId, request.getCategoryId(), json, request.getLabel());
        return R.ok();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        userGameInfoService.deleteById(userId, id);
        return R.ok();
    }

    @lombok.Data
    public static class SaveGameInfoRequest {
        private String gameAccount;
        private String contact;
        private String remark;
    }

    @lombok.Data
    public static class SaveDynamicRequest {
        private Long categoryId;
        private Map<String, String> savedFields;
        private String label;
    }
}
