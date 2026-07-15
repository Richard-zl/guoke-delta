package com.delta.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.user.entity.UserSubscribe;
import com.delta.user.mapper.UserSubscribeMapper;
import com.delta.user.service.UserSubscribeService;
import org.springframework.stereotype.Service;

@Service
public class UserSubscribeServiceImpl extends ServiceImpl<UserSubscribeMapper, UserSubscribe> implements UserSubscribeService {}
