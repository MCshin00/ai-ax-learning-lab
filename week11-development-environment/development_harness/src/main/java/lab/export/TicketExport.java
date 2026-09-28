package lab.export;
import java.util.List;
import java.util.stream.Collectors;
public final class TicketExport {
    private TicketExport() {}
    public static String csv(List<Ticket> tickets) {
        return "id,team,status,title\n" + TicketFilter.select(tickets).stream()
            .map(t -> List.of(t.id(),t.team(),t.status(),t.title()).stream()
                .map(CsvCell::encode).collect(Collectors.joining(",")))
            .collect(Collectors.joining("\n"));
    }
}
