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
}
