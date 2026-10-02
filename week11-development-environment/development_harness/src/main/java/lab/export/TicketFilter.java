package lab.export;
import java.util.List;
public final class TicketFilter {
    private TicketFilter() {}
    public static List<Ticket> select(List<Ticket> tickets) {
        return select(tickets, null);
    }
    public static List<Ticket> select(List<Ticket> tickets, String team) {
        if (team != null && team.isEmpty()) {
            throw new IllegalArgumentException("team must not be empty");
        }
        return tickets.stream()
            .filter(t -> t.status().equals("OPEN") && (team == null || team.equals(t.team())))
            .toList();
    }
}
