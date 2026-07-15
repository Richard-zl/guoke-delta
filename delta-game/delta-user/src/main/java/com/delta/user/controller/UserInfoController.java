package com.delta.user.controller;

import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final UserService userService;

    @GetMapping("/info")
    public R<Map<String, Object>> getUserInfo() {
        Long userId = SecurityUtils.getUserId();
        User user = userService.getById(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("phone", user.getPhone());
        data.put("points", user.getPoints() != null ? user.getPoints() : 0);
        data.put("totalPoints", user.getTotalPoints() != null ? user.getTotalPoints() : 0);
        data.put("levelCode", user.getLevelCode() != null ? user.getLevelCode() : "BRONZE");
        data.put("levelName", user.getLevelName() != null ? user.getLevelName() : "青铜伴星");

        return R.ok(data);
    }
}