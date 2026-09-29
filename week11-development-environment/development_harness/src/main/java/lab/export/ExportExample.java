package lab.export;
import java.util.List;
public final class ExportExample {
    public static void main(String[] args) {
        // 같은 입력의 출력을 비교할 수 있도록 실습 문의 두 개를 고정합니다.
        var rows=List.of(new Ticket("T-1","OPS","OPEN","VPN, 접속"),new Ticket("T-2","APP","CLOSED","로그인"));
        System.out.println(ExportFilename.name());
        System.out.println(TicketExport.csv(rows));
    }
}
