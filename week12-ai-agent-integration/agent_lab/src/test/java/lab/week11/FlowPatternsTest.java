package lab.week11;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FlowPatternsTest {
    @Test void routingAvoidsUnneededSearchAndIndependentFailuresKeepTheirIds() {
        var result=FlowPatterns.route("status","A-102",id -> new OrderLookup.Fact("found",id,null),
            () -> { fail("상태 조회에서 검색하면 안 됩니다."); return null; });
        assertTrue(result.containsKey("order"));
        var rows=FlowPatterns.collectOrders(List.of("A-102","A-999"),id -> {
            if(id.equals("A-999")) throw new IllegalStateException();
            return new OrderLookup.Fact("found",id,null);
        });
        assertEquals("found",rows.get("A-102").status());
        assertEquals("unavailable",rows.get("A-999").status());
    }
    @Test void revisionChecksAgainAndStopsWhenStillWrong() {
        var result=FlowPatterns.reviseOnce("draft",s -> List.of("근거 없음"),(s,f) -> s);
        assertEquals("needs_review",result.status());assertEquals(1,result.revisions());
    }
}
