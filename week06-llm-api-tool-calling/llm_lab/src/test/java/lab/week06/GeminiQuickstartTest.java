package lab.week06;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GeminiQuickstartTest {
    @Test void plainRequestPreservesInputAndHasNoCustomerTools() {
        var calls = new AtomicInteger();
        var result = GeminiQuickstart.run("로그인 수정 완료, 배포 확인 필요.", "test-model",
                (model, input, config) -> {
                    calls.incrementAndGet();
                    assertEquals("test-model", model);
                    assertEquals("로그인 수정 완료, 배포 확인 필요.", input);
                    assertTrue(config.tools().isEmpty());
                    assertTrue(config.systemInstruction().isPresent());
                    assertEquals(2048, config.maxOutputTokens().orElseThrow());
                    return response("STOP", "로그인 수정은 완료되었으며 배포 확인이 필요합니다.");
                });
        assertEquals(1, calls.get());
        assertEquals("MODEL_RESPONSE", result.get("status"));
        assertEquals("UNAVAILABLE", result.get("usage_status"));
        assertNull(result.get("usage"));
    }

    @Test void incompleteOrBlockedResponseIsNotReportedAsCompleted() {
        var incomplete = GeminiQuickstart.run("메모", "test", (m, t, c) -> response("MAX_TOKENS", "로그인"));
        assertEquals("INCOMPLETE", incomplete.get("status"));
        assertEquals("로그인", incomplete.get("answer"));
        var blocked = GeminiQuickstart.run("메모", "test", (m, t, c) ->
                GenerateContentResponse.fromJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        assertEquals("REFUSED", blocked.get("status"));
    }

    @Test void usageKeepsProviderFieldsAndDoesNotFillInMissingCounts() {
        var result = GeminiQuickstart.run("메모", "test", (m, t, c) ->
                GenerateContentResponse.fromJson("""
                    {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"요약"}]}}],
                     "usageMetadata":{"promptTokenCount":20,"candidatesTokenCount":5,
                     "thoughtsTokenCount":3,"totalTokenCount":28}}
                    """));
        JsonNode usage = (JsonNode) result.get("usage");
        assertEquals("REPORTED", result.get("usage_status"));
        assertEquals(5, usage.path("candidatesTokenCount").asInt());
        assertEquals(3, usage.path("thoughtsTokenCount").asInt());
        assertFalse(usage.has("cachedContentTokenCount"));
    }

    @Test void transportFailureHasNoFabricatedUsageOrExceptionDetails() {
        var result = GeminiQuickstart.run("메모", "test", (m, t, c) -> {
            throw new IllegalStateException("private diagnostic");
        });
        assertEquals("PROVIDER_ERROR", result.get("status"));
        assertNull(result.get("usage"));
        assertFalse(result.toString().contains("private diagnostic"));
    }

    @Test void liveExecutionRequiresKeyModelAndExplicitEnableFlag() {
        assertEquals(3, GeminiQuickstart.missingSettings(Map.of()).size());
        assertTrue(GeminiQuickstart.missingSettings(Map.of(
                "GEMINI_API_KEY", "example-only", "GEMINI_MODEL", "test", "AI_AX_LIVE", "1")).isEmpty());
        assertEquals(java.util.List.of("AI_AX_LIVE=1"), GeminiQuickstart.missingSettings(Map.of(
                "GEMINI_API_KEY", "example-only", "GEMINI_MODEL", "test")).stream()
                .filter(value -> value.startsWith("AI_AX_LIVE")).toList());
    }

    private static GenerateContentResponse response(String finish, String text) {
        return GenerateContentResponse.fromJson("{\"candidates\":[{\"finishReason\":\"" + finish
                + "\",\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}");
    }
}
