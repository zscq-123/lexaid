package com.lexaid.community.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the live sessions for each signed-in user. Messages are written through REST; this
 * handler only receives heartbeats and pushes server events to every open tab of a user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, Map.of("type", "READY"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            var payload = objectMapper.readTree(message.getPayload());
            if ("PING".equals(payload.path("type").asText())) {
                send(session, Map.of("type", "PONG"));
            }
        } catch (Exception ignored) {
            // The channel is push-only; malformed client frames are ignored.
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("私信 WebSocket 连接异常: {}", exception.getMessage());
        remove(session);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    public boolean isOnline(long userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && userSessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    public void send(long userId, Object payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        for (WebSocketSession session : List.copyOf(userSessions)) {
            send(session, payload);
        }
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            remove(session);
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("私信消息序列化失败", e);
            return;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("私信 WebSocket 推送失败: {}", e.getMessage());
            remove(session);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void remove(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) return;
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(session);
        if (userSessions.isEmpty()) sessions.remove(userId, userSessions);
    }

    private static Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(MessageHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException ignored) {
        }
    }
}
