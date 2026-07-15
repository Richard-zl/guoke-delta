package com.delta.common.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.chat.domain.entity.ChatMessage;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.mapper.ChatMessageMapper;
import com.delta.common.chat.mapper.ChatSessionMapper;
import com.delta.common.chat.util.ChatParticipantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public ChatSession findOrCreate(long encodedId1, long encodedId2) {
        long[] pair = ChatParticipantId.normalize(encodedId1, encodedId2);
        long id1 = pair[0];
        long id2 = pair[1];
        ChatSession s = getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId1, id1)
                .eq(ChatSession::getId2, id2)
                .eq(ChatSession::getStatus, "ACTIVE"), false);
        if (s == null) {
            s = new ChatSession();
            s.setId1(id1);
            s.setId2(id2);
            s.setStatus("ACTIVE");
            save(s);
        }
        return s;
    }

    public void saveMessage(ChatMessage msg) {
        chatMessageMapper.insert(msg);
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate().eq(ChatSession::getId, msg.getSessionId())
                .set(ChatSession::getLastMessageAt, now).update();
    }

    public Page<ChatMessage> listMessages(Long sessionId, int page, int size) {
        return chatMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt));
    }

    /** 标记会话中非当前用户发送的未读消息为已读，userKey 格式 "USER:123" / "PLAYER:456" / "CS:1" */
    public void markRead(Long sessionId, String userKey) {
        String[] parts = userKey.split(":", 2);
        if (parts.length != 2) return;
        String senderType = parts[0];
        chatMessageMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getIsRead, 0)
                .ne(ChatMessage::getSenderType, senderType)
                .set(ChatMessage::getIsRead, 1));
    }
}
