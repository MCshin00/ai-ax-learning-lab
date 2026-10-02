package lab.export;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ExportAcceptanceTest {
    @Test void simpleOpenTickets() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,VPN 접속", TicketExport.csv(List.of(
            new Ticket("T-1","OPS","OPEN","VPN 접속"),
            new Ticket("T-2","APP","CLOSED","로그인"))));
    }

    @Test void commaInTitleStaysInOneCell() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"VPN, 접속\"\nT-2,APP,OPEN,로그인",
            TicketExport.csv(List.of(
                new Ticket("T-1","OPS","OPEN","VPN, 접속"),
                new Ticket("T-2","APP","OPEN","로그인"),
                new Ticket("T-3","OPS","CLOSED","제외"))));
    }

    @Test void quoteInTitleIsPreserved() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"로그인 \"\"오류\"\"\"",
            TicketExport.csv(List.of(new Ticket("T-1","OPS","OPEN","로그인 \"오류\""))));
    }

    @Test void commaAndQuoteInTitleArePreserved() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"VPN, \"\"접속\"\"\"",
            TicketExport.csv(List.of(new Ticket("T-1","OPS","OPEN","VPN, \"접속\""))));
    }

    @Test void lineBreaksInTitleStayInOneCell() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"첫 줄\n다음 줄\"\nT-2,APP,OPEN,\"첫 줄\r\n다음 줄\"\nT-3,OPS,OPEN,\"첫 줄\r다음 줄\"",
            TicketExport.csv(List.of(
                new Ticket("T-1","OPS","OPEN","첫 줄\n다음 줄"),
                new Ticket("T-2","APP","OPEN","첫 줄\r\n다음 줄"),
                new Ticket("T-3","OPS","OPEN","첫 줄\r다음 줄"))));
    }

    @Test void specifiedTeamKeepsExactOpenMatchesInOrderAndEscapesTitles() {
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"VPN, \"\"접속\"\"\"\nT-5,OPS,OPEN,\"첫 줄\n다음 줄\"",
            TicketExport.csv(List.of(
                new Ticket("T-1","OPS","OPEN","VPN, \"접속\""),
                new Ticket("T-2","APP","OPEN","다른 팀"),
                new Ticket("T-3","OPS","CLOSED","제외"),
                new Ticket("T-4","ops","OPEN","다른 팀"),
                new Ticket("T-5","OPS","OPEN","첫 줄\n다음 줄")), "OPS"));
    }

    @Test void noSelectedTicketsReturnOnlyTheHeader() {
        assertEquals("id,team,status,title", TicketExport.csv(List.of()));
        assertEquals("id,team,status,title", TicketExport.csv(List.of(
            new Ticket("T-1","OPS","CLOSED","제외"),
            new Ticket("T-2","APP","OPEN","다른 팀")), "OPS"));
    }

    @Test void emptyTeamIsRejectedByCsvExport() {
        assertThrows(IllegalArgumentException.class,
            () -> TicketExport.csv(List.of(new Ticket("T-1","OPS","OPEN","제외")), ""));
    }

    @Test void duplicateIdsAreRemovedAfterOpenAndTeamSelection() {
        List<Ticket> tickets = List.of(
            new Ticket("T-1", "OPS", "CLOSED", "옛 기록"),
            new Ticket("T-1", "OPS", "OPEN", "새 기록"),
            new Ticket("T-2", "OPS", "OPEN", "첫 제목"),
            new Ticket("T-2", "OPS", "OPEN", "수정 제목"),
            new Ticket("T-3", "APP", "OPEN", "다른 팀"));

        ExportResult result = TicketExport.export(tickets, "OPS");

        assertEquals("id,team,status,title\nT-1,OPS,OPEN,새 기록\nT-2,OPS,OPEN,첫 제목",
            result.csv());
        assertEquals(1, result.excludedDuplicateCount());
        assertEquals(2, result.exportedCount());
        assertEquals(result.csv(), TicketExport.csv(tickets, "OPS"));
    }

    @Test void duplicateIdsAcrossTeamsCountWhenTeamIsOmitted() {
        ExportResult result = TicketExport.export(List.of(
            new Ticket("T-1", "APP", "OPEN", "첫 항목"),
            new Ticket("T-1", "OPS", "OPEN", "뒤 항목"),
            new Ticket("T-2", "OPS", "OPEN", "다른 문의")));

        assertEquals("id,team,status,title\nT-1,APP,OPEN,첫 항목\nT-2,OPS,OPEN,다른 문의",
            result.csv());
        assertEquals(1, result.excludedDuplicateCount());
        assertEquals(2, result.exportedCount());
    }

    @Test void countsAreZeroForDuplicatesWhenNoneAreSelectedOrRepeated() {
        ExportResult single = TicketExport.export(List.of(
            new Ticket("T-1", "OPS", "OPEN", "첫 문의")));
        assertEquals(0, single.excludedDuplicateCount());
        assertEquals(1, single.exportedCount());

        ExportResult empty = TicketExport.export(List.of(
            new Ticket("T-2", "OPS", "CLOSED", "제외")));
        assertEquals("id,team,status,title", empty.csv());
        assertEquals(0, empty.excludedDuplicateCount());
        assertEquals(0, empty.exportedCount());
    }

    @Test void emptyTeamIsRejectedByExportResult() {
        assertThrows(IllegalArgumentException.class,
            () -> TicketExport.export(List.of(), ""));
    }

    @Test void firstAndLastSelectionKeepIdOrderAndCounts() {
        List<Ticket> tickets = List.of(
            new Ticket("T-1", "OPS", "CLOSED", "옛 기록"),
            new Ticket("T-1", "OPS", "OPEN", "첫 제목"),
            new Ticket("T-2", "OPS", "OPEN", "다른 문의"),
            new Ticket("T-1", "OPS", "OPEN", "수정 제목"),
            new Ticket("T-3", "APP", "OPEN", "다른 팀"));

        ExportResult first = TicketExport.export(tickets, "OPS", DuplicateSelection.FIRST);
        ExportResult last = TicketExport.export(tickets, "OPS", DuplicateSelection.LAST);

        assertEquals("id,team,status,title\nT-1,OPS,OPEN,첫 제목\nT-2,OPS,OPEN,다른 문의", first.csv());
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,수정 제목\nT-2,OPS,OPEN,다른 문의", last.csv());
        assertEquals(first, TicketExport.export(tickets, "OPS"));
        assertEquals(first.csv(), TicketExport.csv(tickets, "OPS"));
        for (ExportResult result : List.of(first, last)) {
            assertEquals(1, result.excludedDuplicateCount());
            assertEquals(2, result.exportedCount());
        }
    }

    @Test void lastSelectionIgnoresLaterClosedAndOtherTeamRecords() {
        ExportResult result = TicketExport.export(List.of(
            new Ticket("T-1", "OPS", "OPEN", "첫 제목"),
            new Ticket("T-1", "OPS", "OPEN", "수정 제목"),
            new Ticket("T-1", "OPS", "CLOSED", "닫힌 제목"),
            new Ticket("T-1", "APP", "OPEN", "다른 팀"),
            new Ticket("T-1", "ops", "OPEN", "대소문자가 다른 팀")), "OPS", DuplicateSelection.LAST);

        assertEquals("id,team,status,title\nT-1,OPS,OPEN,수정 제목", result.csv());
        assertEquals(1, result.excludedDuplicateCount());
        assertEquals(1, result.exportedCount());
    }

    @Test void lastSelectionAcrossTeamsRetainsTheFirstIdPosition() {
        List<Ticket> tickets = List.of(
            new Ticket("T-1", "APP", "OPEN", "첫 항목"),
            new Ticket("T-2", "OPS", "OPEN", "다른 문의"),
            new Ticket("T-1", "OPS", "OPEN", "중간 항목"),
            new Ticket("T-1", "APP", "OPEN", "마지막 항목"));

        ExportResult last = TicketExport.export(tickets, null, DuplicateSelection.LAST);
        ExportResult first = TicketExport.export(tickets, null, DuplicateSelection.FIRST);

        assertEquals("id,team,status,title\nT-1,APP,OPEN,마지막 항목\nT-2,OPS,OPEN,다른 문의", last.csv());
        assertEquals("id,team,status,title\nT-1,APP,OPEN,첫 항목\nT-2,OPS,OPEN,다른 문의", first.csv());
        assertEquals(first, TicketExport.export(tickets));
        assertEquals(first, TicketExport.export(tickets, null));
        assertEquals(first.csv(), TicketExport.csv(tickets));
        assertEquals(first.csv(), TicketExport.csv(tickets, null));
        for (ExportResult result : List.of(first, last)) {
            assertEquals(2, result.excludedDuplicateCount());
            assertEquals(2, result.exportedCount());
        }
    }

    @Test void lastSelectionEscapesTheAdoptedTitleAndPreservesLineBreaks() {
        for (String lineBreak : List.of("\n", "\r\n", "\r")) {
            ExportResult result = TicketExport.export(List.of(
                new Ticket("T-1", "OPS", "OPEN", "첫 제목"),
                new Ticket("T-1", "OPS", "OPEN", "VPN, \"접속\"" + lineBreak + "다음 줄")),
                "OPS", DuplicateSelection.LAST);

            assertEquals("id,team,status,title\nT-1,OPS,OPEN,\"VPN, \"\"접속\"\"" + lineBreak + "다음 줄\"", result.csv());
            assertEquals(1, result.excludedDuplicateCount());
            assertEquals(1, result.exportedCount());
        }
    }

    @Test void lastSelectionHandlesEmptyAndNonDuplicateResults() {
        ExportResult single = TicketExport.export(List.of(
            new Ticket("T-1", "OPS", "OPEN", "첫 문의")), "OPS", DuplicateSelection.LAST);
        assertEquals("id,team,status,title\nT-1,OPS,OPEN,첫 문의", single.csv());
        assertEquals(0, single.excludedDuplicateCount());
        assertEquals(1, single.exportedCount());

        for (List<Ticket> tickets : List.of(List.<Ticket>of(), List.of(
                new Ticket("T-1", "OPS", "CLOSED", "제외"),
                new Ticket("T-2", "APP", "OPEN", "다른 팀")))) {
            ExportResult empty = TicketExport.export(tickets, "OPS", DuplicateSelection.LAST);
            assertEquals("id,team,status,title", empty.csv());
            assertEquals(0, empty.excludedDuplicateCount());
            assertEquals(0, empty.exportedCount());
        }
    }

    @Test void explicitSelectionRejectsEmptyTeamAndNullSelection() {
        assertThrows(IllegalArgumentException.class,
            () -> TicketExport.export(List.of(), "", DuplicateSelection.LAST));
        assertThrows(IllegalArgumentException.class,
            () -> TicketExport.export(List.of(), null, null));
    }
}
