package com.lexaid.community.messages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTicketServiceTest {
    private final MessageTicketService tickets = new MessageTicketService();

    @Test
    void ticketIsSingleUseAndBoundToTheIssuingUser() {
        String ticket = tickets.issue(42);
        assertEquals(42, tickets.consume(ticket));
        assertNull(tickets.consume(ticket));
    }

    @Test
    void missingOrUnknownTicketsAreRejected() {
        assertNull(tickets.consume(null));
        assertNull(tickets.consume(""));
        assertNull(tickets.consume("not-issued"));
    }
}
