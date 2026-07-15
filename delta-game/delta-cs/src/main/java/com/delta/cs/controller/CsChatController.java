package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.chat.domain.entity.ChatMessage;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.mapper.ChatMessageMapper;
import com.delta.common.chat.mapper.ChatSessionMapper;
import com.delta.common.chat.service.ChatConversationQueryService;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cs/chat")
@RequiredArgsConstructor
public class CsChatController {
    private final ChatConversationQueryService conversationQueryService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping("/sessions")
    public R<Page<ChatSession>> sessions(PageQuery query, @RequestParam(required = false) String type) {
        Long myId = SecurityUtils.getUserId();
        String myType = SecurityUtils.getUserType();
        Page<ChatSession> page = conversationQueryService.pageCsSessions(myId, myType, type, query);
        return R.ok(page);
    }

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

    @GetMapping("/message/list")
    public R<Page<ChatMessage>> messageList(@RequestParam Long sessionId, PageQuery query) {
        LambdaQueryWrapper<ChatMessage> w = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        return R.ok(chatMessageMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), w));
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
}
