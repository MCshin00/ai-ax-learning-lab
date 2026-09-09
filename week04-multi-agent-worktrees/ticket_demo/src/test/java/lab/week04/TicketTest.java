package lab.week04;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TicketTest {
    @Test void existingLabelsAndTrim() { assertEquals("[높음] 로그인 오류", App.renderTicket(" 로그인 오류 ", "P1")); }
    @Test void unknownPriorityIsVisible() { assertEquals("[CUSTOM] 요청", App.renderTicket("요청", "CUSTOM")); }
    @Test void p0NormalizesTitleInFinalDisplay() { assertEquals("[긴급] 로그인 오류", App.renderTicket("  로그인   오류  ", "P0")); }
    @Test void whitespaceOnlyP0RequiresOwnerCheck() {
        for (String title : new String[]{"   ", " \t\n ", "\u00a0\u3000"})
            assertEquals("[긴급] 담당자 확인 필요", App.renderTicket(title, "P0"));
    }
    @Test void whitespaceOnlyNonurgentTitleKeepsPlaceholder() {
        for (String priority : new String[]{"P1", "P2", "P3", "CUSTOM"})
            assertEquals("[" + Priorities.priorityLabel(priority) + "] (제목 없음)", App.renderTicket("   ", priority));
    }
    @Test void literalPlaceholderIsPreservedForP0() { assertEquals("[긴급] (제목 없음)", App.renderTicket("(제목 없음)", "P0")); }
}
