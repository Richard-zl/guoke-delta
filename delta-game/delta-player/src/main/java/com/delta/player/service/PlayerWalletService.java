package com.delta.player.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.player.entity.PlayerWallet;

public interface PlayerWalletService extends IService<PlayerWallet> {
    PlayerWallet getByPlayerId(Long playerId);
    void initWallet(Long playerId);
}
