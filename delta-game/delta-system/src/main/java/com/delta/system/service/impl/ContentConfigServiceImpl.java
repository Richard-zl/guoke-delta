package com.delta.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.system.entity.ContentConfig;
import com.delta.system.mapper.ContentConfigMapper;
import com.delta.system.service.ContentConfigService;
import org.springframework.stereotype.Service;

@Service
public class ContentConfigServiceImpl extends ServiceImpl<ContentConfigMapper, ContentConfig> implements ContentConfigService {}
