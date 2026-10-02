package lab.export;
import java.time.LocalDate;
import java.util.List;
public final class ExportExample {
    public static void main(String[] args) {
        // 같은 팀과 날짜로 첫 기록·마지막 기록의 파일 이름, CSV, 건수를 비교합니다.
        var rows=List.of(
            new Ticket("T-1","OPS","CLOSED","옛 기록"),
            new Ticket("T-1","OPS","OPEN","첫 제목"),
            new Ticket("T-2","OPS","OPEN","다른 문의"),
            new Ticket("T-1","OPS","OPEN","수정 제목"),
            new Ticket("T-3","APP","OPEN","다른 팀"));
        LocalDate date = LocalDate.of(2026, 9, 3);
        String team = "OPS";
        for (DuplicateSelection selection : DuplicateSelection.values()) {
            ExportResult result = TicketExport.export(rows, team, selection);
            System.out.println("중복 선택: " + selection);
            System.out.println(ExportFilename.name(date, team));
            System.out.println(result.csv());
            System.out.println("제외한 중복 수: " + result.excludedDuplicateCount());
            System.out.println("실제로 내보낸 수: " + result.exportedCount());
        }
    }
}
