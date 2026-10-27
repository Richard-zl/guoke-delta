package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.player.entity.Player;
import com.delta.player.mapper.PlayerMapper;
import com.delta.player.service.PlayerReentryRules;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerServiceImpl extends ServiceImpl<PlayerMapper, Player> implements PlayerService {
    private final PlayerWalletService playerWalletService;

    @Override
    public Player getByOpenid(String openid) {
        return getOne(new LambdaQueryWrapper<Player>().eq(Player::getOpenid, openid));
    }

    @Override
    public boolean idTakenIncludingDeleted(Long id) {
        if (id == null) {
            return false;
        }
        return baseMapper.selectIdIncludingDeleted(id) != null;
    }

    @Override
    public void releaseDeletedOpenid(String openid) {
        if (openid == null || openid.isBlank()) {
            return;
        }
        Long deletedId = baseMapper.selectDeletedIdByOpenid(openid);
        if (deletedId == null) {
            return;
        }
        String released = PlayerReentryRules.releasedOpenid(openid, deletedId);
        if (released.equals(openid)) {
            return;
        }
        baseMapper.releaseDeletedOpenid(deletedId, released);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Player loginOrRegister(String openid, String nickname, String avatar) {
        Player player = getByOpenid(openid);
        if (player == null) {
            releaseDeletedOpenid(openid);
            player = new Player();
            player.setOpenid(openid);
            player.setNickname(nickname);
            player.setAvatar(avatar);
            player.setStatus("PENDING");
            save(player);
            playerWalletService.initWallet(player.getId());
        }
        return player;
    }
}
