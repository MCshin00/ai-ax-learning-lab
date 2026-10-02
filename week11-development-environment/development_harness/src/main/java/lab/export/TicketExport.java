package lab.export;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
public final class TicketExport {
    private static final String HEADER = "id,team,status,title";
    private TicketExport() {}
    public static String csv(List<Ticket> tickets) {
        return export(tickets).csv();
    }
    public static String csv(List<Ticket> tickets, String team) {
        return export(tickets, team).csv();
    }
    public static ExportResult export(List<Ticket> tickets) {
        return export(tickets, null);
    }
    public static ExportResult export(List<Ticket> tickets, String team) {
        return export(tickets, team, DuplicateSelection.FIRST);
    }
    public static ExportResult export(List<Ticket> tickets, String team,
                                      DuplicateSelection duplicateSelection) {
        if (duplicateSelection == null) {
            throw new IllegalArgumentException("duplicateSelection must not be null");
        }
        List<Ticket> selected = TicketFilter.select(tickets, team);
        var uniqueById = new LinkedHashMap<String, Ticket>();
        for (Ticket ticket : selected) {
            if (duplicateSelection == DuplicateSelection.LAST) {
                uniqueById.put(ticket.id(), ticket);
            } else {
                uniqueById.putIfAbsent(ticket.id(), ticket);
            }
        }
        String rows = uniqueById.values().stream()
            .map(t -> List.of(t.id(),t.team(),t.status(),t.title()).stream()
                .map(CsvCell::encode).collect(Collectors.joining(",")))
            .collect(Collectors.joining("\n"));
        String csv = rows.isEmpty() ? HEADER : HEADER + "\n" + rows;
        return new ExportResult(csv, selected.size() - uniqueById.size(), uniqueById.size());
    }
}
