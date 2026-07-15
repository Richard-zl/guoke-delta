package com.delta.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.message.entity.SystemNotification;

public interface SystemNotificationService extends IService<SystemNotification> {
    void send(String receiverType, Long receiverId, String title, String content, String bizType, Long relatedId);

    int unreadCount(String receiverType, Long receiverId);

    void markAllRead(String receiverType, Long receiverId);
}
