package com.delta.player.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.player.entity.PlayerAccount;
import com.delta.player.mapper.PlayerAccountMapper;
import com.delta.player.service.PlayerAccountService;
import org.springframework.stereotype.Service;

@Service
public class PlayerAccountServiceImpl extends ServiceImpl<PlayerAccountMapper, PlayerAccount> implements PlayerAccountService {}
