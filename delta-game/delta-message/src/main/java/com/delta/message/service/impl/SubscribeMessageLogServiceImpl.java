package com.delta.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.message.entity.SubscribeMessageLog;
import com.delta.message.mapper.SubscribeMessageLogMapper;
import com.delta.message.service.SubscribeMessageLogService;
import org.springframework.stereotype.Service;

@Service
public class SubscribeMessageLogServiceImpl extends ServiceImpl<SubscribeMessageLogMapper, SubscribeMessageLog> implements SubscribeMessageLogService {}
