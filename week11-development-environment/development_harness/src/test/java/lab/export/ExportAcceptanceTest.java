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
}
