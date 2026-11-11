package com.lexaid.community.messages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageNotifier {

    private final MessageWebSocketHandler sockets;
    private final MessageService messages;

    public void messageCreated(MessageService.Message message) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "MESSAGE");
            event.put("message", message);
            sockets.send(message.senderId(), withUnread(event, messages.unreadTotal(message.senderId())));
            sockets.send(message.recipientId(), withUnread(event, messages.unreadTotal(message.recipientId())));
        } catch (RuntimeException e) {
            // The message is already committed; a push failure must not make the sender retry it.
            log.warn("私信实时推送未完成", e);
        }
    }

    public void messageRecalled(MessageService.Message message) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "RECALL");
            event.put("message", message);
            sockets.send(message.senderId(), withUnread(event, messages.unreadTotal(message.senderId())));
            sockets.send(message.recipientId(), withUnread(event, messages.unreadTotal(message.recipientId())));
        } catch (RuntimeException e) {
            log.warn("私信撤回推送未完成", e);
        }
    }

    public void conversationCleared(long userId, long peerId, long unreadTotal) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "CLEAR");
        event.put("peerId", peerId);
        event.put("unreadTotal", unreadTotal);
        sockets.send(userId, event);
    }

    public void messageDeleted(long userId, long messageId, long unreadTotal) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "DELETE");
        event.put("messageId", messageId);
        event.put("unreadTotal", unreadTotal);
        sockets.send(userId, event);
    }

    public void reminder(long userId, String preview) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "REMINDER");
        event.put("preview", preview);
        sockets.send(userId, event);
    }

    public void messagesRead(long readerId, long peerId, LocalDateTime readAt) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "READ");
            event.put("readerId", readerId);
            event.put("peerId", peerId);
            event.put("readAt", readAt);
            sockets.send(readerId, withUnread(event, messages.unreadTotal(readerId)));
            sockets.send(peerId, withUnread(event, messages.unreadTotal(peerId)));
        } catch (RuntimeException e) {
            log.warn("私信已读回执推送未完成", e);
        }
    }

    private static Map<String, Object> withUnread(Map<String, Object> event, long unread) {
        Map<String, Object> copy = new LinkedHashMap<>(event);
        copy.put("unreadTotal", unread);
        return copy;
    }
}
