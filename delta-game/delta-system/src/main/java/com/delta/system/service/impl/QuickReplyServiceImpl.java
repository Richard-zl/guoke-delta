package com.delta.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.system.entity.QuickReply;
import com.delta.system.mapper.QuickReplyMapper;
import com.delta.system.service.QuickReplyService;
import org.springframework.stereotype.Service;

@Service
public class QuickReplyServiceImpl extends ServiceImpl<QuickReplyMapper, QuickReply> implements QuickReplyService {}
