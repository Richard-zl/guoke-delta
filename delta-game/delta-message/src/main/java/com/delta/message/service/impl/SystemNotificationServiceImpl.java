package com.delta.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.message.entity.SystemNotification;
import com.delta.message.mapper.SystemNotificationMapper;
import com.delta.message.service.SystemNotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SystemNotificationServiceImpl extends ServiceImpl<SystemNotificationMapper, SystemNotification> implements SystemNotificationService {

    @Override
    public void send(String receiverType, Long receiverId, String title, String content, String bizType, Long relatedId) {
        SystemNotification n = new SystemNotification();
        n.setReceiverType(receiverType);
        n.setReceiverId(receiverId != null ? receiverId : 0L);
        n.setTitle(title != null ? title : "");
        n.setContent(content);
        n.setBizType(bizType);
        n.setRelatedId(relatedId);
        n.setIsRead(0);
        n.setCreatedAt(LocalDateTime.now());
        save(n);
    }

    @Override
    public int unreadCount(String receiverType, Long receiverId) {
        LambdaQueryWrapper<SystemNotification> w = new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getIsRead, 0);
        if ("CS".equals(receiverType)) {
            w.eq(SystemNotification::getReceiverType, "CS")
                    .and(a -> a.eq(SystemNotification::getReceiverId, 0).or().eq(SystemNotification::getReceiverId, receiverId));
        } else {
            w.eq(SystemNotification::getReceiverType, receiverType)
                    .eq(SystemNotification::getReceiverId, receiverId != null ? receiverId : 0L);
        }
        return (int) count(w);
    }

    @Override
    public void markAllRead(String receiverType, Long receiverId) {
        LambdaUpdateWrapper<SystemNotification> w = new LambdaUpdateWrapper<SystemNotification>()
                .eq(SystemNotification::getIsRead, 0)
                .set(SystemNotification::getIsRead, 1);
        if ("CS".equals(receiverType)) {
            w.eq(SystemNotification::getReceiverType, "CS")
                    .and(a -> a.eq(SystemNotification::getReceiverId, 0).or().eq(SystemNotification::getReceiverId, receiverId));
        } else {
            w.eq(SystemNotification::getReceiverType, receiverType)
                    .eq(SystemNotification::getReceiverId, receiverId != null ? receiverId : 0L);
        }
        update(w);
    }
}
