package com.delta.common.chat.handler;

import com.alibaba.fastjson2.JSON;
import com.delta.common.chat.domain.entity.ChatMessage;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.service.ChatServiceImpl;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.common.security.service.TokenService;
import com.delta.common.websocket.manager.WebSocketSessionManager;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionManager sessionManager;
    private final ChatServiceImpl chatService;
    private final TokenService tokenService;

    /** session -> userKey 映射，用于断开时清理 */
    private final ConcurrentHashMap<String, String> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 从URL参数中解析JWT token
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        String token = extractParam(query, "token");
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket连接缺少token, 关闭连接");
            closeQuietly(session);
            return;
        }
        Claims claims = tokenService.parseToken(token);
        if (claims == null) {
            log.warn("WebSocket token解析失败, 关闭连接");
            closeQuietly(session);
            return;
        }
        Long userId = claims.get("userId", Long.class);
        // 专用聊天页通过 URL 参数 chatRole 声明身份，便于同一 token 下区分用户/打手/客服
        String chatRole = extractParam(query, "chatRole");
        String userType = isValidChatRole(chatRole) ? ("ADMIN".equals(chatRole) ? "CS" : chatRole) : (String) claims.get("userType");
        String userKey = WebSocketSessionManager.buildKey(userType, userId);

        // 注册到SessionManager
        sessionManager.add(userKey, session);
        sessionUserMap.put(session.getId(), userKey);
        log.info("WebSocket connected: {} -> {}", session.getId(), userKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = JSON.parseObject(message.getPayload());
        String msgType = (String) data.get("type");

        // 心跳消息：只做应答，不落库、不推送
        if ("ping".equalsIgnoreCase(msgType)) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            } catch (Exception e) {
                log.warn("发送pong心跳失败", e);
            }
            return;
        }

        String action = (String) data.getOrDefault("action", "send");

        if ("markRead".equals(action)) {
            // 标记已读
            Long sessionId = Long.parseLong(data.get("sessionId").toString());
            String userKey = sessionUserMap.get(session.getId());
            if (userKey != null) {
                chatService.markRead(sessionId, userKey);
            }
            return;
        }

        // 发送消息
        Long sid = Long.parseLong(data.get("sessionId").toString());
        ChatSession chatSession = chatService.getById(sid);
        if (chatSession == null || chatSession.getId1() == null || chatSession.getId2() == null) {
            log.warn("WebSocket发送: 会话不存在或无效, sessionId={}", sid);
            return;
        }

        String userKey = sessionUserMap.get(session.getId());
        if (userKey == null) {
            log.warn("WebSocket会话未绑定用户, 丢弃消息");
            closeQuietly(session);
            return;
        }
        String[] parts = userKey.split(":", 2);
        if (parts.length != 2) {
            log.warn("非法的userKey格式: {}", userKey);
            closeQuietly(session);
            return;
        }
        String senderType = parts[0];
        long rawSenderId;
        try {
            rawSenderId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("userKey中的用户ID解析失败: {}", userKey);
            closeQuietly(session);
            return;
        }
        String chatRole = (String) data.get("chatRole");
        if (isValidChatRole(chatRole)) senderType = "ADMIN".equals(chatRole) ? "CS" : chatRole;
        long encodedSenderId = ChatParticipantId.encode(senderType, rawSenderId);
        if (!isSessionMember(chatSession, encodedSenderId)) {
            log.warn("WebSocket发送: 非会话成员禁止发消息, encodedId={}, sessionId={}", encodedSenderId, sid);
            return;
        }

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sid);
        msg.setSenderType(senderType);
        msg.setSenderId(rawSenderId);
        msg.setType(msgType != null ? msgType : "TEXT");

        String content = (String) data.get("content");
        msg.setContent(filterSensitiveWords(content));
        msg.setIsRead(0);
        chatService.saveMessage(msg);

        pushToSessionMembers(msg);
    }

    private boolean isSessionMember(ChatSession s, long myEncodedId) {
        if (s == null) return false;
        return (s.getId1() != null && s.getId1().equals(myEncodedId)) || (s.getId2() != null && s.getId2().equals(myEncodedId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userKey = sessionUserMap.remove(session.getId());
        if (userKey != null) {
            sessionManager.remove(userKey);
        }
        log.info("WebSocket disconnected: {} -> {}", session.getId(), userKey);
    }

    /** 推送消息给会话中另一参与者（排除发送者） */
    private void pushToSessionMembers(ChatMessage msg) {
        ChatSession s = chatService.getById(msg.getSessionId());
        if (s == null || s.getId1() == null || s.getId2() == null) return;
        String senderKey = msg.getSenderType() + ":" + msg.getSenderId();
        long senderEncoded = ChatParticipantId.encode(msg.getSenderType(), msg.getSenderId());
        String msgJson = JSON.toJSONString(msg);
        // 对方 id1 或 id2（编码ID）→ userType:rawId
        long otherEncoded = s.getId1().equals(senderEncoded) ? s.getId2() : s.getId1();
        String otherType = ChatParticipantId.getEntityType(otherEncoded) == 1 ? "USER" :
                ChatParticipantId.getEntityType(otherEncoded) == 2 ? "PLAYER" : "CS";
        long rawOther = ChatParticipantId.getRawId(otherEncoded);
        pushToMember(otherType, String.valueOf(rawOther), senderKey, msgJson);
        if ("CS".equals(otherType)) pushToMember("ADMIN", String.valueOf(rawOther), senderKey, msgJson);
    }

    private static boolean isValidChatRole(String role) {
        return "USER".equals(role) || "PLAYER".equals(role) || "CS".equals(role) || "ADMIN".equals(role);
    }

    private void pushToMember(String userType, String memberId, String senderKey, String msgJson) {
        String key = userType + ":" + memberId;
        if (key.equals(senderKey)) return; // 不推送给发送者
        WebSocketSession memberSession = sessionManager.get(key);
        if (memberSession != null && memberSession.isOpen()) {
            try {
                memberSession.sendMessage(new TextMessage(msgJson));
            } catch (IOException e) {
                log.warn("推送消息到{}失败", key);
            }
        }
    }

    private String filterSensitiveWords(String content) {
        if (content == null) return null;
        String[] words = {"开挂", "外挂", "代练骗子"};
        for (String w : words) content = content.replace(w, "***");
        return content;
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) return kv[1];
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try { session.close(); } catch (IOException ignored) {}
    }
}
