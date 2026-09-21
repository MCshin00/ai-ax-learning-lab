package lab.week10;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryIntegrationTest {
    @Test void missingTitleSuppressesEveryPriority() {
        for (String priority : new String[] {null, "urgent", "normal", "low", "unknown"}) {
            assertEquals("확인 필요", Summary.render(priority, null));
            assertEquals("확인 필요", Summary.render(priority, ""));
            assertEquals("확인 필요", Summary.render(priority, " \t\n\r\f\u000B "));
        }
    }

    @Test void literalNoticeRemainsAnOrdinaryTitle() {
        assertEquals("[P0] 확인 필요", Summary.render("urgent", " 확인   필요 "));
    }

    @Test void whitespaceOutsideTheContractRemainsTitleContent() {
        assertEquals("[P1] \u00A0", Summary.render(null, "\u00A0"));
    }
}
