package lab.week08;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuickstartTest {
    Map<String, Object> run(String query, Quickstart.Generator generator) throws Exception {
        return Quickstart.run(query, Quickstart.KNOWLEDGE_BASE, .12, generator);
    }
    @Test void evidenceIsVisibleBeforeGeneration() throws Exception {
        var result = run("이중 결제 환불 접수 기한", null);
        assertEquals("EVIDENCE_READY", result.get("status"));
        assertTrue(((Map<?, ?>) result.get("sources")).containsKey("refund-policy"));
        assertEquals(false, result.get("model_called"));
    }
    @Test void noEvidenceStopsWithoutModelCall() throws Exception {
        var result = run("사내 주차비 지원 한도는 얼마인가요?", (q, sources) -> { fail("No-evidence path called model"); return null; });
        assertEquals("ABSTAINED", result.get("status"));
    }
    @Test void fabricatedOrMissingCitationsAreRejected() throws Exception {
        for (var ids : List.of(List.of("invented"), List.of()))
            assertEquals("INVALID_OUTPUT", run("이중 결제 환불 접수 기한",
                    (q, s) -> Map.of("status", "ANSWERED", "answer", "30일입니다.", "source_ids", ids)).get("status"));
    }
    @Test void generationReceivesTheActualQueryAndSelectedSources() throws Exception {
        var result = run("이중 결제 환불 접수 기한", (query, sources) -> {
            assertTrue(query.contains("환불")); assertTrue(sources.containsKey("refund-policy"));
            return Map.of("status", "ANSWERED", "answer", "원문 확인이 필요한 생성 예시입니다.", "source_ids", List.of("refund-policy"));
        });
        assertEquals("ANSWERED", result.get("status")); assertEquals(true, result.get("model_called"));
    }
    @Test void modelAbstentionCannotPublishAnUnsupportedClaim() throws Exception {
        var result = run("이중 결제 환불 접수 기한", (q, s) -> Map.of("status", "ABSTAINED", "answer", "90일입니다.", "source_ids", List.of()));
        assertEquals("ABSTAINED", result.get("status")); assertFalse(result.get("answer").toString().contains("90일"));
    }
    @Test void malformedOutputCannotBecomeSuccess() throws Exception {
        for (var output : List.<Map<String, Object>>of(Map.of(), Map.of("status", "ANSWERED", "answer", " ", "source_ids", List.of("refund-policy")),
                Map.of("status", "OTHER", "answer", "value", "source_ids", List.of())))
            assertEquals("INVALID_OUTPUT", run("이중 결제 환불 접수 기한", (q, s) -> output).get("status"));
    }
    @Test void providerFailureIsDifferentFromInsufficientEvidence() throws Exception {
        var result = run("이중 결제 환불 접수 기한", (q, s) -> { throw new IllegalStateException("fixture failure"); });
        assertEquals("PROVIDER_ERROR", result.get("status"));
        assertEquals(true, result.get("model_called"));
        assertFalse(((Map<?, ?>) result.get("sources")).isEmpty());
    }
    @Test void validCitationDoesNotProveFactualSupport() throws Exception {
        var result = run("이중 결제 환불 접수 기한", (q, s) -> Map.of("status", "ANSWERED",
                "answer", "365일입니다.", "source_ids", List.of("refund-policy")));
        assertEquals("ANSWERED", result.get("status"));
        // This deliberately passes structural validation. Golden-answer review must catch the false claim.
        assertFalse(((Map<?, ?>) result.get("sources")).get("refund-policy").toString().contains("365일"));
    }
}
