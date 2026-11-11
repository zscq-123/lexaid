package com.lexaid.community.messages;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tickets for the WebSocket handshake. The JWT itself never appears in
 * a WebSocket URL or server access log.
 */
@Service
public class MessageTicketService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(long userId) {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        String token = UUID.randomUUID().toString();
        tickets.put(token, new Ticket(userId, now.plus(TTL)));
        return token;
    }

    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        Ticket ticket = tickets.remove(token);
        if (ticket == null || ticket.expiresAt().isBefore(Instant.now())) return null;
        return ticket.userId();
    }

    public long ttlSeconds() {
        return TTL.toSeconds();
    }

    private record Ticket(long userId, Instant expiresAt) {}
}
