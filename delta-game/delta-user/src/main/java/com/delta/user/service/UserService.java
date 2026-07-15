package com.delta.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.user.entity.User;

public interface UserService extends IService<User> {
    User getByOpenid(String openid);
    User getByPhone(String phone);
    User loginOrRegister(String openid, String nickname, String avatar);
    User loginOrRegisterByPhone(String phone);
    void syncUserProfileToPlayerIfSameOpenid(Long userId);
}
