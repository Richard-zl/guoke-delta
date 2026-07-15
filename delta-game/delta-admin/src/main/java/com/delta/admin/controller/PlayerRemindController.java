package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.message.service.SystemNotificationService;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.service.OrderPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 打手端提醒红点：被邀请数、系统通知未读、消息未读（放在 admin 避免 player→message 循环依赖） */
@RestController
@RequestMapping("/player/remind")
@RequiredArgsConstructor
public class PlayerRemindController {
    private final OrderPlayerService orderPlayerService;
    private final SystemNotificationService systemNotificationService;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping
    public R<Map<String, Object>> remind() {
        Long playerId = SecurityUtils.getUserId();
        long encodedId = ChatParticipantId.encodePlayer(playerId);

        long inviteCount = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        int systemUnread = systemNotificationService.unreadCount("PLAYER", playerId);
        int messageUnread = crossModuleMapper.selectTotalChatUnread(encodedId, "PLAYER");

        Map<String, Object> map = new HashMap<>();
        map.put("inviteCount", inviteCount);
        map.put("systemUnread", systemUnread);
        map.put("messageUnread", messageUnread);
        return R.ok(map);
    }
}
