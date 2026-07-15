package com.delta.message.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.message.entity.SystemNotification;
import com.delta.message.service.SystemNotificationService;
import com.delta.user.entity.UserSubscribe;
import com.delta.user.service.UserSubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {
    private final SystemNotificationService systemNotificationService;
    private final UserSubscribeService userSubscribeService;
    private final CrossModuleMapper crossModuleMapper;

    /** 系统通知列表（原 message/list，按接收者维度） */
    @GetMapping("/list")
    public R<Page<SystemNotification>> list(PageQuery query,
                                            @RequestParam(value = "type", required = false) String type,
                                            @RequestParam(value = "userType", required = false) String userType) {
        String resolvedType = type != null ? type : userType;
        String receiverType = resolveReceiverType();
        Long receiverId = SecurityUtils.getUserId();
        LambdaQueryWrapper<SystemNotification> w = new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getReceiverType, receiverType)
                .orderByDesc(SystemNotification::getCreatedAt);
        if ("CS".equals(receiverType)) {
            w.and(q -> q.eq(SystemNotification::getReceiverId, 0).or().eq(SystemNotification::getReceiverId, receiverId));
        } else {
            w.eq(SystemNotification::getReceiverId, receiverId);
        }
        if (resolvedType != null && !resolvedType.isEmpty()) {
            w.eq(SystemNotification::getBizType, resolvedType);
        }
        return R.ok(systemNotificationService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }

    /** 系统通知未读数（原 message/unread-count） */
    @GetMapping("/unread-count")
    public R<Integer> unreadCount(@RequestParam(value = "type", required = false) String type,
                                  @RequestParam(value = "userType", required = false) String userType) {
        String receiverType = resolveReceiverType();
        Long receiverId = SecurityUtils.getUserId();
        return R.ok(systemNotificationService.unreadCount(receiverType, receiverId));
    }

    /**
     * 提醒汇总：系统通知未读 + 聊天消息未读（分离展示用）
     * 用户端/打手端：systemUnread, messageUnread
     */
    @GetMapping("/remind")
    public R<Map<String, Object>> remind() {
        String receiverType = resolveReceiverType();
        Long receiverId = SecurityUtils.getUserId();
        long encodedId = ChatParticipantId.encode(receiverType, receiverId);
        String excludeSenderType = "CS".equals(receiverType) || "ADMIN".equals(receiverType) ? "CS" : receiverType;

        int systemUnread = systemNotificationService.unreadCount(receiverType, receiverId);
        int messageUnread = crossModuleMapper.selectTotalChatUnread(encodedId, excludeSenderType);

        Map<String, Object> map = new HashMap<>();
        map.put("systemUnread", systemUnread);
        map.put("messageUnread", messageUnread);
        return R.ok(map);
    }

    @PostMapping("/read/{id}")
    public R<Void> markRead(@PathVariable Long id) {
        SystemNotification n = new SystemNotification();
        n.setId(id);
        n.setIsRead(1);
        systemNotificationService.updateById(n);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Void> markAllRead(@RequestBody(required = false) Map<String, String> body,
                               @RequestParam(value = "userType", required = false) String userTypeParam) {
        String receiverType = userTypeParam != null ? userTypeParam : (body != null ? body.getOrDefault("type", body.getOrDefault("userType", null)) : null);
        if (receiverType == null || receiverType.isEmpty()) return R.fail("type参数不能为空");
        Long receiverId = SecurityUtils.getUserId();
        systemNotificationService.markAllRead(receiverType, receiverId);
        return R.ok();
    }

    @PostMapping("/subscribe/report")
    public R<Void> reportSubscribe(@RequestBody Map<String, String> body) {
        String templateId = body.get("templateId");
        String status = body.get("status");
        if (templateId == null) return R.fail("templateId不能为空");
        Long userId = SecurityUtils.getUserId();
        int statusVal = "accept".equals(status) ? 1 : 0;
        var existing = userSubscribeService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserSubscribe>()
                        .eq(UserSubscribe::getUserId, userId)
                        .eq(UserSubscribe::getTemplateId, templateId));
        if (existing != null) {
            existing.setStatus(statusVal);
            userSubscribeService.updateById(existing);
        } else {
            UserSubscribe sub = new UserSubscribe();
            sub.setUserId(userId);
            sub.setTemplateId(templateId);
            sub.setStatus(statusVal);
            userSubscribeService.save(sub);
        }
        return R.ok();
    }

    private String resolveReceiverType() {
        String t = SecurityUtils.getUserType();
        return t != null ? t.toUpperCase() : "USER";
    }
}
