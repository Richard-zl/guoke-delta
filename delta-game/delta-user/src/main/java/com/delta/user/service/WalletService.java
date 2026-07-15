package com.delta.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.user.entity.Wallet;

public interface WalletService extends IService<Wallet> {
    Wallet getByUserId(Long userId);
    void initWallet(Long userId);
}
