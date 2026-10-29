package com.delta.common.websocket.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void add(String key, WebSocketSession session) { sessions.put(key, session); }
    public void remove(String key) { sessions.remove(key); }
    public WebSocketSession get(String key) { return sessions.get(key); }
    public boolean isOnline(String key) { return sessions.containsKey(key); }
    public int getOnlineCount() { return sessions.size(); }

    public static String buildKey(String userType, Long userId) {
        return userType + ":" + userId;
    }

    /**
     * 按身份前缀推送文本。key 形如 CS:1、ADMIN:2，只发给指定身份的在线连接。
     *
     * @return 成功写入的连接数
     */
    public int sendTextToUserTypes(Set<String> userTypes, String payload) {
        if (payload == null || userTypes == null || userTypes.isEmpty()) return 0;
        int sent = 0;
        for (var entry : sessions.entrySet()) {
            String key = entry.getKey();
            int split = key.indexOf(':');
            if (split <= 0 || !userTypes.contains(key.substring(0, split))) continue;
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) continue;
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                sent++;
            } catch (IOException e) {
                log.warn("WebSocket推送失败 key={}", key);
            }
        }
        return sent;
    }
}
