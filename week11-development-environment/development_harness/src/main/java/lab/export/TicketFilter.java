package lab.export;
import java.util.List;
public final class TicketFilter {
    private TicketFilter() {}
    public static List<Ticket> select(List<Ticket> tickets) {
        return tickets.stream().filter(t -> t.status().equals("OPEN")).toList();
    }
}
