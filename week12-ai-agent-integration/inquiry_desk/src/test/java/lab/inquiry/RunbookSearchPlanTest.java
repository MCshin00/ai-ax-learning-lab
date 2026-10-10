package lab.inquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RunbookSearchPlanTest {
    private final RunbookSearch search = new RunbookSearch(Path.of("data"));
    @TempDir Path temp;

    @Test void s1_connection() {
        var result = search.search("VPN", "연결이 자꾸 끊김");
        assertEquals(RunbookSearch.Outcome.FOUND, result.outcome());
        assertEquals(List.of("RB-VPN"), result.documents().stream().map(RunbookSearch.Runbook::id).toList());
    }

    @Test void s2_certificateFirst() {
        var result = search.search("VPN", "접속이 안 됨 인증서 만료");
        assertEquals(RunbookSearch.Outcome.FOUND, result.outcome());
        assertEquals(List.of("RB-VPN-CERT", "RB-VPN"), result.documents().stream().map(RunbookSearch.Runbook::id).toList());
    }

    @Test void s3_noMatchingWords() {
        var result = search.search("VPN", "접속이 안 됨");
        assertEquals(RunbookSearch.Outcome.NONE, result.outcome());
        assertTrue(result.documents().isEmpty());
    }

    @Test void s4_filterService() {
        var result = search.search("SSO", "인증서 만료");
        assertEquals(RunbookSearch.Outcome.NONE, result.outcome());
        assertTrue(result.documents().isEmpty());
    }

    @Test void s5_missingFile() {
        assertEquals(RunbookSearch.Outcome.FAILED, new RunbookSearch(temp).search("VPN", "연결이 자꾸 끊김").outcome());
    }
}
