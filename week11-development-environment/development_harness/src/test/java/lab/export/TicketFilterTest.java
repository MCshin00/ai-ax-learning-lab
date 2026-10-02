package lab.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketFilterTest {
    private final Ticket opsOpen = new Ticket("T-1", "OPS", "OPEN", "첫 문의");
    private final Ticket appClosed = new Ticket("T-2", "APP", "CLOSED", "제외");
    private final Ticket appOpen = new Ticket("T-3", "APP", "OPEN", "둘째 문의");
    private final Ticket opsClosed = new Ticket("T-4", "OPS", "CLOSED", "제외");
    private final Ticket opsOpenAgain = new Ticket("T-5", "OPS", "OPEN", "셋째 문의");

    @Test void omittedTeamSelectsAllOpenTicketsInInputOrder() {
        List<Ticket> tickets = List.of(opsOpen, appClosed, appOpen, opsClosed, opsOpenAgain);

        assertEquals(List.of(opsOpen, appOpen, opsOpenAgain), TicketFilter.select(tickets));
        assertEquals(List.of(opsOpen, appOpen, opsOpenAgain), TicketFilter.select(tickets, null));
    }

    @Test void specifiedTeamSelectsExactMatchAndOpenStatusInInputOrder() {
        Ticket lowercaseTeam = new Ticket("T-6", "ops", "OPEN", "제외");
        Ticket trailingSpaceTeam = new Ticket("T-7", "OPS ", "OPEN", "제외");
        List<Ticket> tickets = List.of(opsOpen, lowercaseTeam, opsClosed, appOpen,
            trailingSpaceTeam, opsOpenAgain);

        assertEquals(List.of(opsOpen, opsOpenAgain), TicketFilter.select(tickets, "OPS"));
    }

    @Test void noMatchingOpenTicketsReturnsEmptyList() {
        assertEquals(List.of(), TicketFilter.select(List.of(appClosed, appOpen), "OPS"));
    }

    @Test void emptyTeamIsInvalid() {
        assertThrows(IllegalArgumentException.class,
            () -> TicketFilter.select(List.of(opsOpen), ""));
    }
}
