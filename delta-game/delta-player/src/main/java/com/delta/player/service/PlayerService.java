package com.delta.player.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.player.entity.Player;

public interface PlayerService extends IService<Player> {
    Player getByOpenid(String openid);
    Player loginOrRegister(String openid, String nickname, String avatar);
}
