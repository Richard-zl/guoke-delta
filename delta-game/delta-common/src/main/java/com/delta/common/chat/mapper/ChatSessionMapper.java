package com.delta.common.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.common.chat.domain.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {}
