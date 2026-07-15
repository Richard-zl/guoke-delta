package com.delta.player.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.player.entity.Withdraw;
import com.delta.player.mapper.WithdrawMapper;
import com.delta.player.service.WithdrawService;
import org.springframework.stereotype.Service;

@Service
public class WithdrawServiceImpl extends ServiceImpl<WithdrawMapper, Withdraw> implements WithdrawService {}
