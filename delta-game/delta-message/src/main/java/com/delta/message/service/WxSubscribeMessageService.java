package com.delta.message.service;

import java.util.Map;

/**
 * 微信订阅消息发送服务
 */
public interface WxSubscribeMessageService {

    /**
     * 异步发送微信订阅消息
     *
     * @param userType   接收者类型: USER / PLAYER
     * @param userId     接收者ID
     * @param templateId 消息模板ID
     * @param data       模板数据 key -> value（不含 .DATA 后缀，由实现层自动拼接）
     * @param page       点击跳转的小程序页面路径，可为null
     */
    void sendAsync(String userType, Long userId, String templateId,
                   Map<String, String> data, String page);
}
