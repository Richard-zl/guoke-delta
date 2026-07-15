package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.chat.domain.entity.ChatMessage;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.mapper.ChatMessageMapper;
import com.delta.common.chat.mapper.ChatSessionMapper;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台聊天记录管理（只读，用于投诉核查、合规审计）
 */
@RestController
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CrossModuleMapper crossModuleMapper;

    /**
     * 查看所有聊天会话（展示参与者昵称与最后一条消息，只读）
     */
    @GetMapping("/session/list")
    public R<Page<ChatSession>> sessionList(PageQuery query,
                                            @RequestParam(required = false) Long participantId) {
        LambdaQueryWrapper<ChatSession> w = new LambdaQueryWrapper<ChatSession>()
                .and(participantId != null, q -> q.eq(ChatSession::getId1, participantId).or().eq(ChatSession::getId2, participantId))
                .orderByDesc(ChatSession::getLastMessageAt);
        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (ChatSession s : page.getRecords()) {
            enrichSession(s);
        }
        return R.ok(page);
    }

    private void enrichSession(ChatSession s) {
        Long id1 = s.getId1();
        Long id2 = s.getId2();
        String name1 = id1 != null ? crossModuleMapper.selectNicknameByEncodedId(id1) : null;
        String name2 = id2 != null ? crossModuleMapper.selectNicknameByEncodedId(id2) : null;
        s.setTargetName((name1 != null ? name1 : "ID:" + id1) + " / " + (name2 != null ? name2 : "ID:" + id2));
        String lastType = crossModuleMapper.selectLastMessageType(s.getId());
        if (lastType != null) {
            switch (lastType) {
                case "IMAGE" -> s.setLastMessage("[图片]");
                case "PRODUCT" -> s.setLastMessage("[商品]");
                case "ORDER" -> s.setLastMessage("[订单]");
                default -> s.setLastMessage(crossModuleMapper.selectLastMessageContent(s.getId()));
            }
        }
    }

    /**
     * 查看会话消息（管理员只读，按时间正序便于阅读）
     */
    @GetMapping("/message/list")
    public R<Page<ChatMessage>> messageList(@RequestParam Long sessionId, PageQuery query) {
        LambdaQueryWrapper<ChatMessage> w = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return R.ok(chatMessageMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }
}
