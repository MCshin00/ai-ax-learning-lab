package lab.export;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExportIntegrationTest {
    @Test void teamFilenameAndSelectedRecordsUseTheSameTeam() {
        List<Ticket> tickets = List.of(
            new Ticket("T-1", "OPS", "CLOSED", "옛 기록"),
            new Ticket("T-1", "OPS", "OPEN", "첫 제목"),
            new Ticket("T-2", "OPS", "OPEN", "다른 문의"),
            new Ticket("T-1", "OPS", "OPEN", "수정 제목"),
            new Ticket("T-3", "APP", "OPEN", "다른 팀"));
        String team = "OPS";
        LocalDate date = LocalDate.of(2026, 9, 3);

        for (DuplicateSelection selection : DuplicateSelection.values()) {
            ExportResult result = TicketExport.export(tickets, team, selection);
            String title = selection == DuplicateSelection.FIRST ? "첫 제목" : "수정 제목";
            assertEquals("tickets-OPS-2026-09-03.csv", ExportFilename.name(date, team));
            assertEquals("id,team,status,title\nT-1,OPS,OPEN," + title + "\nT-2,OPS,OPEN,다른 문의",
                result.csv());
            assertEquals(1, result.excludedDuplicateCount());
            assertEquals(2, result.exportedCount());
        }
        assertEquals(TicketExport.export(tickets, team, DuplicateSelection.FIRST),
            TicketExport.export(tickets, team));
    }

    @Test void filenameEscapingKeepsTheOriginalTeamForFiltering() {
        String team = "OPS/지원";
        LocalDate date = LocalDate.of(2026, 9, 3);
        List<Ticket> tickets = List.of(
            new Ticket("T-1", team, "OPEN", "첫 제목"),
            new Ticket("T-2", "OPS%2F지원", "OPEN", "별도 팀"),
            new Ticket("T-1", team, "OPEN", "수정 제목"));

        ExportResult result = TicketExport.export(tickets, team, DuplicateSelection.LAST);

        assertEquals("tickets-OPS%2F지원-2026-09-03.csv", ExportFilename.name(date, team));
        assertEquals("tickets-OPS%252F지원-2026-09-03.csv", ExportFilename.name(date, "OPS%2F지원"));
        assertEquals("id,team,status,title\nT-1,OPS/지원,OPEN,수정 제목", result.csv());
        assertEquals(1, result.excludedDuplicateCount());
        assertEquals(1, result.exportedCount());
    }

    @Test void omittedTeamUsesAllTeamsAndTheDateOnlyFilename() {
        String team = null;
        LocalDate date = LocalDate.of(2026, 9, 4);
        List<Ticket> tickets = List.of(
            new Ticket("T-1", "OPS", "OPEN", "첫 제목"),
            new Ticket("T-2", "OPS", "OPEN", "다른 문의"),
            new Ticket("T-1", "APP", "OPEN", "수정 제목"));

        ExportResult last = TicketExport.export(tickets, team, DuplicateSelection.LAST);

        assertEquals("tickets-2026-09-04.csv", ExportFilename.name(date, team));
        assertEquals(ExportFilename.name(date), ExportFilename.name(date, team));
        assertEquals("id,team,status,title\nT-1,APP,OPEN,수정 제목\nT-2,OPS,OPEN,다른 문의", last.csv());
        assertEquals(1, last.excludedDuplicateCount());
        assertEquals(2, last.exportedCount());
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,첫 제목\nT-2,OPS,OPEN,다른 문의",
            TicketExport.export(tickets).csv());
    }
}
