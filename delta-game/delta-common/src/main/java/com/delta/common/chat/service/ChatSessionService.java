package com.delta.common.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.common.chat.domain.entity.ChatSession;

/**
 * 私聊会话：id1/id2 为编码ID（ChatParticipantId 编码），id1 &lt; id2
 */
public interface ChatSessionService extends IService<ChatSession> {
    /** 根据两个参与者编码ID查找或创建会话（自动规范化 id1&lt;id2） */
    ChatSession findOrCreate(long encodedId1, long encodedId2);
}
