package com.delta.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.system.entity.SensitiveWord;
import com.delta.system.mapper.SensitiveWordMapper;
import com.delta.system.service.SensitiveWordService;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord> implements SensitiveWordService {}
