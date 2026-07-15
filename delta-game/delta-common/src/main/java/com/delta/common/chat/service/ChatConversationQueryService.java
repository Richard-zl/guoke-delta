package com.delta.common.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.mapper.ChatSessionMapper;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.common.domain.PageQuery;
import com.delta.common.mapper.CrossModuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 客服侧会话列表：基于 chat_session(id1/id2) 查询
 */
@Service
@RequiredArgsConstructor
public class ChatConversationQueryService {

    private final ChatSessionMapper sessionMapper;
    private final CrossModuleMapper crossModuleMapper;

    /** 客服侧：按当前 CS 编码ID 查询参与会话（id1=me OR id2=me） */
    public Page<ChatSession> pageCsSessions(Long adminId, String adminType, String type, PageQuery query) {
        long encodedCs = ChatParticipantId.encodeCs(adminId);
        LambdaQueryWrapper<ChatSession> qw = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, "ACTIVE")
                .and(q -> q.eq(ChatSession::getId1, encodedCs).or().eq(ChatSession::getId2, encodedCs))
                .orderByDesc(ChatSession::getLastMessageAt);
        Page<ChatSession> page = sessionMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), qw);
        for (ChatSession s : page.getRecords()) {
            enrichForCs(s, encodedCs);
        }
        return page;
    }

    private void enrichForCs(ChatSession s, long myEncodedId) {
        long targetId = s.getId1() != null && s.getId1().equals(myEncodedId) ? s.getId2() : s.getId1();
        s.setTargetId(targetId);
        s.setTargetName(crossModuleMapper.selectNicknameByEncodedId(targetId));
        s.setAvatar(crossModuleMapper.selectAvatarByEncodedId(targetId));
        String lastType = crossModuleMapper.selectLastMessageType(s.getId());
        if (lastType != null) {
            switch (lastType) {
                case "IMAGE" -> s.setLastMessage("[图片]");
                case "PRODUCT" -> s.setLastMessage("[商品]");
                case "ORDER" -> s.setLastMessage("[订单]");
                default -> s.setLastMessage(crossModuleMapper.selectLastMessageContent(s.getId()));
            }
        }
        s.setUnreadCount(crossModuleMapper.selectUnreadCount(s.getId(), "CS"));
    }
}
