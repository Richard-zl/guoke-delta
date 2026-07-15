package com.delta.common.websocket.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
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
}
