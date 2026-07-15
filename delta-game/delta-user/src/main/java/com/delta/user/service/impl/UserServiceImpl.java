package com.delta.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.user.entity.User;
import com.delta.user.mapper.UserMapper;
import com.delta.user.service.UserService;
import com.delta.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final WalletService walletService;
    private final CrossModuleMapper crossModuleMapper;

    private static final String[] PREFIXES = {
            "玩家", "勇者", "冒险家", "旅行者", "星辰", "闪电", "暗影", "烈焰", "极光", "流星"
    };

    /** 生成随机昵称，如"勇者8321" */
    private String generateRandomNickname() {
        String prefix = PREFIXES[ThreadLocalRandom.current().nextInt(PREFIXES.length)];
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return prefix + suffix;
    }

    @Override
    public User getByOpenid(String openid) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    @Override
    public User getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User loginOrRegister(String openid, String nickname, String avatar) {
        User user = getByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname((nickname != null && !nickname.isEmpty()) ? nickname : generateRandomNickname());
            user.setAvatar(avatar);
            user.setStatus(1);
            save(user);
            walletService.initWallet(user.getId());
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User loginOrRegisterByPhone(String phone) {
        User user = getByPhone(phone);
        if (user == null) {
            user = new User();
            user.setOpenid("h5:" + phone);
            user.setNickname(generateRandomNickname());
            user.setAvatar("");
            user.setPhone(phone);
            user.setStatus(1);
            save(user);
            walletService.initWallet(user.getId());
        } else if (user.getPhone() == null || user.getPhone().isEmpty()) {
            user.setPhone(phone);
            updateById(user);
        }
        return user;
    }

    @Override
    public void syncUserProfileToPlayerIfSameOpenid(Long userId) {
        User user = getById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isEmpty()) return;
        String nickname = user.getNickname() != null ? user.getNickname() : "";
        String avatar = user.getAvatar() != null ? user.getAvatar() : "";
        crossModuleMapper.updatePlayerProfileByOpenid(user.getOpenid(), nickname, avatar);
    }
}
