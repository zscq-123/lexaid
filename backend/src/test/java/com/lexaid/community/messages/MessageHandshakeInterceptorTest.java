package com.lexaid.community.messages;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageHandshakeInterceptorTest {
    private final MessageTicketService tickets = mock(MessageTicketService.class);
    private final MessageHandshakeInterceptor interceptor = new MessageHandshakeInterceptor(tickets);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @Test
    void aValidTicketBindsTheUserToTheSession() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/messages?ticket=abc"));
        when(tickets.consume("abc")).thenReturn(7L);
        var attributes = new HashMap<String, Object>();
        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertEquals(7L, attributes.get(MessageHandshakeInterceptor.USER_ID_ATTRIBUTE));
    }

    @Test
    void aMissingOrUnknownTicketIsRejected() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/messages"));
        when(tickets.consume(null)).thenReturn(null);
        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
