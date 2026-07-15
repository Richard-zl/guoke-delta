package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.mapper.PlayerWalletMapper;
import com.delta.player.service.PlayerWalletService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PlayerWalletServiceImpl extends ServiceImpl<PlayerWalletMapper, PlayerWallet> implements PlayerWalletService {

    @Override
    public PlayerWallet getByPlayerId(Long playerId) {
        return getOne(new LambdaQueryWrapper<PlayerWallet>().eq(PlayerWallet::getPlayerId, playerId));
    }

    @Override
    public void initWallet(Long playerId) {
        PlayerWallet wallet = new PlayerWallet();
        wallet.setPlayerId(playerId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setFrozenAmount(BigDecimal.ZERO);
        wallet.setTotalIncome(BigDecimal.ZERO);
        save(wallet);
    }
}
