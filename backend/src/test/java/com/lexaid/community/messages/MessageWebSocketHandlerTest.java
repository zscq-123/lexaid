package com.lexaid.community.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class MessageWebSocketHandlerTest {

    @Test
    void reportsWhetherAPayloadReachedAnOpenSession() throws Exception {
        MessageWebSocketHandler handler = new MessageWebSocketHandler(new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(MessageHandshakeInterceptor.USER_ID_ATTRIBUTE, 42L));
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        reset(session);
        when(session.getAttributes()).thenReturn(Map.of(MessageHandshakeInterceptor.USER_ID_ATTRIBUTE, 42L));
        when(session.isOpen()).thenReturn(true);
        assertEquals(1, handler.send(42L, Map.of("type", "REMINDER")));

        doThrow(new IOException("closed")).when(session).sendMessage(any(TextMessage.class));
        assertEquals(0, handler.send(42L, Map.of("type", "REMINDER")));
    }
}
