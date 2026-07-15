package com.delta.common.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.common.chat.domain.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {}
