package com.delta.common.chat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.chat.domain.entity.ChatMessage;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.service.ChatSmsReminderService;
import com.delta.common.chat.service.ChatServiceImpl;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.chat.util.ChatParticipantId;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/common/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatServiceImpl chatService;
    private final ChatSmsReminderService chatSmsReminderService;
    private final JdbcTemplate jdbcTemplate;
    private final CrossModuleMapper crossModuleMapper;

    /** 会话列表：id1=我的编码ID OR id2=我的编码ID */
    @GetMapping("/session/list")
    public R<?> sessionList(@RequestParam(required = false) String chatRole) {
        long myEncodedId = getMyEncodedId(chatRole);
        LambdaQueryWrapper<ChatSession> qw = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, "ACTIVE")
                .and(q -> q.eq(ChatSession::getId1, myEncodedId).or().eq(ChatSession::getId2, myEncodedId))
                .orderByDesc(ChatSession::getLastMessageAt);
        List<ChatSession> all = chatService.list(qw);
        for (ChatSession s : all) {
            enrichSession(s, myEncodedId);
        }
        return R.ok(all);
    }

    /** 根据两个参与者编码ID创建/查找会话（前端可传 id1/id2 或 userId+userType 等） */
    @PostMapping("/session/create")
    public R<ChatSession> createSession(@RequestParam long id1, @RequestParam long id2) {
        ChatSession s = chatService.findOrCreate(id1, id2);
        long myEncodedId = getMyEncodedId(null);
        enrichSession(s, myEncodedId);
        return R.ok(s);
    }

    /** 根据订单获取用户-打手会话（从订单解析 user_id、player_id 后查会话） */
    @GetMapping("/session/by-order/{orderId}")
    public R<ChatSession> sessionByOrder(@PathVariable Long orderId,
                                         @RequestParam(required = false) String chatRole) {
        long myEncodedId = getMyEncodedId(chatRole);
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id, player_id FROM `order` WHERE id = ?", orderId);
        if (rows.isEmpty()) return R.fail("订单不存在");
        java.util.Map<String, Object> row = rows.get(0);
        Long userId = ((Number) row.get("user_id")).longValue();
        Number playerIdNum = (Number) row.get("player_id");
        if (playerIdNum == null) return R.fail("该订单还未指派打手，暂无法聊天");
        long playerId = playerIdNum.longValue();
        long encoded1 = ChatParticipantId.encodeUser(userId);
        long encoded2 = ChatParticipantId.encodePlayer(playerId);
        ChatSession s = chatService.findOrCreate(encoded1, encoded2);
        if (!isSessionMember(s, myEncodedId)) return R.fail("无权限查看该会话");
        enrichSession(s, myEncodedId);
        return R.ok(s);
    }

    @GetMapping("/session/{id}")
    public R<ChatSession> sessionDetail(@PathVariable Long id,
                                        @RequestParam(required = false) String chatRole) {
        ChatSession s = chatService.getById(id);
        if (s == null) return R.fail("会话不存在");
        long myEncodedId = getMyEncodedId(chatRole);
        if (!isSessionMember(s, myEncodedId)) return R.fail("无权限查看该会话");
        enrichSession(s, myEncodedId);
        return R.ok(s);
    }

    @GetMapping("/message/list")
    public R<Page<ChatMessage>> messageList(@RequestParam Long sessionId,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String chatRole) {
        ChatSession s = chatService.getById(sessionId);
        if (s == null) return R.fail("会话不存在");
        long myEncodedId = getMyEncodedId(chatRole);
        if (!isSessionMember(s, myEncodedId)) return R.fail("无权限查看该会话消息");
        return R.ok(chatService.listMessages(sessionId, pageNum, pageSize));
    }

    @PostMapping("/message/read")
    public R<Void> markRead(@RequestParam Long sessionId, @RequestParam(required = false) String chatRole) {
        ChatSession s = chatService.getById(sessionId);
        if (s == null) return R.fail("会话不存在");
        long myEncodedId = getMyEncodedId(chatRole);
        if (!isSessionMember(s, myEncodedId)) return R.fail("无权限操作该会话");
        String userType = ChatParticipantId.getEntityType(myEncodedId) == 1 ? "USER" :
                ChatParticipantId.getEntityType(myEncodedId) == 2 ? "PLAYER" : "CS";
        chatService.markRead(sessionId, userType + ":" + ChatParticipantId.getRawId(myEncodedId));
        return R.ok();
    }

    /** 用户发起客服会话：分配在线客服 */
    @PostMapping("/session/cs")
    public R<ChatSession> createCsSession() {
        long userId = SecurityUtils.getUserId();
        long encodedUser = ChatParticipantId.encodeUser(userId);
        List<java.util.Map<String, Object>> csList = jdbcTemplate.queryForList(
                "SELECT id FROM admin WHERE role = 'CS' AND status = 1 AND deleted = 0 ORDER BY RAND() LIMIT 1");
        if (csList.isEmpty()) return R.fail("暂无可用客服，请稍后再试");
        long csId = ((Number) csList.get(0).get("id")).longValue();
        long encodedCs = ChatParticipantId.encodeCs(csId);
        ChatSession s = chatService.findOrCreate(encodedUser, encodedCs);
        enrichSession(s, encodedUser);
        return R.ok(s);
    }

    @PostMapping("/message/send")
    public R<?> send(@RequestBody ChatMessage msg, @RequestParam(required = false) String chatRole) {
        Long userId = SecurityUtils.getUserId();
        String userType = resolveChatRole(chatRole);
        if (userType == null) userType = SecurityUtils.getUserType();
        if (msg.getSenderType() == null) msg.setSenderType(userType != null && (userType.equals("CS") || userType.equals("ADMIN")) ? "CS" : (userType != null ? userType : "USER"));
        msg.setSenderId(userId);
        msg.setIsRead(0);
        msg.setCreatedAt(LocalDateTime.now());
        chatService.saveMessage(msg);
        return R.ok();
    }

    @PostMapping("/sms-reminder")
    public R<Void> sendSmsReminder(@RequestBody SmsReminderRequest request,
                                   @RequestParam(required = false) String chatRole) {
        long myEncodedId = getMyEncodedId(chatRole);
        chatSmsReminderService.sendReminder(request.getSessionId(), myEncodedId, request.getReminderCode());
        return R.ok();
    }

    private long getMyEncodedId(String chatRole) {
        Long rawId = SecurityUtils.getUserId();
        String type = resolveChatRole(chatRole);
        if (type == null) type = SecurityUtils.getUserType();
        if ("PLAYER".equals(type)) {
            Long playerId = crossModuleMapper.selectPlayerIdByUserId(rawId);
            return ChatParticipantId.encodePlayer(playerId != null ? playerId : rawId);
        }
        if ("CS".equals(type) || "ADMIN".equals(type)) return ChatParticipantId.encodeCs(rawId);
        return ChatParticipantId.encodeUser(rawId);
    }

    private static String resolveChatRole(String chatRole) {
        if (chatRole == null) return null;
        if ("USER".equals(chatRole) || "PLAYER".equals(chatRole) || "CS".equals(chatRole)) return chatRole;
        if ("ADMIN".equals(chatRole)) return "CS";
        return null;
    }

    private boolean isSessionMember(ChatSession s, long myEncodedId) {
        return s != null && (s.getId1() != null && s.getId1().equals(myEncodedId) || s.getId2() != null && s.getId2().equals(myEncodedId));
    }

    private void enrichSession(ChatSession s, long myEncodedId) {
        long targetId = (s.getId1() != null && s.getId1().equals(myEncodedId)) ? s.getId2() : s.getId1();
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
        String excludeType = ChatParticipantId.getEntityType(myEncodedId) == 1 ? "USER" :
                ChatParticipantId.getEntityType(myEncodedId) == 2 ? "PLAYER" : "CS";
        s.setUnreadCount(crossModuleMapper.selectUnreadCount(s.getId(), excludeType));
    }

    @Data
    public static class SmsReminderRequest {
        private Long sessionId;
        private String reminderCode;
    }
}
