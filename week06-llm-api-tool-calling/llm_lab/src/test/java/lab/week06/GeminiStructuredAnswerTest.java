package lab.week06;

import com.google.genai.types.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GeminiStructuredAnswerTest {
    private static final String NORMAL = """
        {"customer_id":"C-100","plan":"basic","account_status":null,
         "needs_follow_up":false,"answer":"basic 요금제입니다."}
        """;

    @Test void schemaIsAppliedAfterTheRealProjectionAndOriginalCallIsPreserved() {
        for (String scenario : List.of("normal", "status", "both", "unknown")) {
            var count = new AtomicInteger();
            var original = GeminiToolLoop.offline(scenario).generate("test", List.of(Content.fromParts(Part.fromText("조회"))), GeminiToolLoop.requestConfig());
            var originalContent = original.candidates().orElseThrow().get(0).content().orElseThrow();
            var result = run("조회", (model, history, config) -> {
                assertEquals("test", model);
                if (count.incrementAndGet() == 1) {
                    assertTrue(config.responseMimeType().isEmpty());
                    assertEquals(GeminiToolLoop.TOOL, config.tools().orElseThrow().get(0));
                    return original;
                }
                assertSame(originalContent, history.get(1));
                assertEquals("application/json", config.responseMimeType().orElseThrow());
                assertEquals(GeminiStructuredAnswer.SCHEMA, config.responseJsonSchema().orElseThrow());
                assertTrue(config.tools().isEmpty());
                assertEquals(2048, config.maxOutputTokens().orElseThrow());
                assertEquals(GeminiQuickstart.requestConfig().thinkingConfig(), config.thinkingConfig());
                var call = originalContent.parts().orElseThrow().get(0).functionCall().orElseThrow();
                var response = history.get(2).parts().orElseThrow().get(0).functionResponse().orElseThrow();
                assertEquals(call.id(), response.id());
                return GeminiStructuredOffline.gateway().generate(model, history, config);
            });
            assertEquals(2, count.get());
            assertEquals("MODEL_RESPONSE", result.get("status"));
            assertEquals(scenario.equals("unknown") ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT", result.get("next_action"));
            var data = (Map<?, ?>) result.get("data");
            assertEquals(List.of("normal", "both").contains(scenario) ? "basic" : null, data.get("plan"));
            assertEquals(List.of("status", "both").contains(scenario) ? "active" : null, data.get("account_status"));
            assertTrue(result.containsKey("structured_response"));
        }
    }

    @Test void missingIdThenFollowupKeepsAcceptedOriginalResponseAndFreshFacts() throws Exception {
        var history = new ArrayList<Content>();
        var outputs = new ArrayList<Content>();
        var count = new AtomicInteger();
        GeminiToolLoop.Gateway gateway = (model, sent, config) -> {
            int number = count.incrementAndGet();
            if (number == 2) assertEquals(1, sent.size()); // Discard the unstructured draft.
            if (number == 3) {
                assertEquals(3, sent.size());
                assertSame(outputs.get(1), sent.get(1));
            }
            var response = GeminiStructuredOffline.gateway().generate(model, sent, config);
            outputs.add(response.candidates().orElseThrow().get(0).content().orElseThrow());
            return response;
        };
        var first = GeminiToolLoop.run("요금제를 알려주세요", "test", gateway, history, true);
        var second = GeminiToolLoop.run("C-100", "test", gateway, history, true);
        assertEquals("ASK_CUSTOMER_ID", first.get("next_action"));
        assertEquals("SHOW_ACCOUNT", second.get("next_action"));
        assertEquals(2, first.get("model_requests"));
        assertEquals(2, second.get("model_requests"));
        assertTrue(((List<?>) first.get("tool_results")).isEmpty());
        assertEquals(6, history.size());
        assertSame(outputs.get(3), history.get(5));
        var status = GeminiToolLoop.run("C-100 고객의 계정 상태를 알려주세요.", "test", GeminiStructuredOffline.gateway(), history, true);
        assertNull(((Map<?, ?>) status.get("data")).get("plan")); // Earlier plan must not leak.
        assertEquals("active", ((Map<?, ?>) status.get("data")).get("account_status"));
    }

    @Test void rejectsWrongFactsMissingFieldsTypesDuplicatesAndTrailingJson() {
        for (String raw : List.of(NORMAL.replace("basic", "premium"), NORMAL.replace("C-100", "C-200"),
                NORMAL.replace("false", "true"), NORMAL.replace("null", "\"active\""))) {
            assertEquals("FACT_MISMATCH", GeminiStructuredAnswer.consume(raw, evidence()).get("status"));
        }
        for (String raw : List.of("{}", "[]", "null", NORMAL.replace("false", "\"false\""),
                NORMAL.replace("null", "17"), NORMAL + "{}", NORMAL.replace("\"plan\":", "\"plan\":null,\"plan\":"),
                NORMAL.substring(0, NORMAL.length() - 3))) {
            var result = GeminiStructuredAnswer.consume(raw, evidence());
            assertEquals("INVALID_OUTPUT", result.get("status"), raw);
            assertEquals("HOLD", result.get("next_action"));
            assertFalse(result.containsKey("data"));
        }
        // A valid shape alone cannot invent a successful lookup.
        assertEquals("FACT_MISMATCH", GeminiStructuredAnswer.consume(NORMAL, List.of()).get("status"));
        // The field comparison deliberately does not claim semantic verification of free prose.
        assertEquals("SHOW_ACCOUNT", GeminiStructuredAnswer.consume(
                NORMAL.replace("basic 요금제입니다.", "premium 요금제입니다."), evidence()).get("next_action"));
    }

    @Test void refusalIncompleteWrongFactsUnexpectedToolAndTransportFailureHoldAfterLookup() {
        var bad = new LinkedHashMap<String, GenerateContentResponse>();
        bad.put("REFUSED", GenerateContentResponse.fromJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        bad.put("INCOMPLETE", GenerateContentResponse.fromJson(GeminiStructuredOffline.answer("{").toJson().replace("STOP", "MAX_TOKENS")));
        bad.put("FACT_MISMATCH", GeminiStructuredOffline.answer(NORMAL.replace("basic", "premium")));
        bad.put("INVALID_OUTPUT", GeminiToolLoop.offline("normal").generate("test", List.of(Content.fromParts(Part.fromText("조회"))), GeminiToolLoop.requestConfig()));
        bad.put("PROVIDER_ERROR", null);
        for (var entry : bad.entrySet()) {
            var history = new ArrayList<Content>();
            var result = GeminiToolLoop.run("조회", "test", (model, sent, config) -> {
                if (config.responseMimeType().isEmpty()) return GeminiToolLoop.offline("normal").generate(model, sent, config);
                if (entry.getValue() == null) throw new IllegalStateException("private diagnostic");
                return entry.getValue();
            }, history, true);
            assertEquals(entry.getKey(), result.get("status"));
            assertEquals("HOLD", result.get("next_action"));
            assertEquals("", result.get("answer"));
            assertFalse(result.containsKey("data"));
            assertEquals(1, ((List<?>) result.get("tool_results")).size());
            assertEquals(3, history.size()); // Rejected output is not kept as conversation truth.
            assertFalse(result.toString().contains("private diagnostic"));
        }
    }

    @Test void invalidToolArgumentsAreNotMisrepresentedAsMissingCustomer() {
        var result = run("조회", (m,h,c) -> GenerateContentResponse.fromJson("""
            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{
            "functionCall":{"name":"get_customer_context","args":{"customer_id":"C-100","fields":["email"]}}}]}}]}
            """));
        assertEquals("TOOL_ERROR", result.get("status"));
        assertEquals("HOLD", result.get("next_action"));
        assertEquals(1, result.get("model_requests"));
    }

    @Test void structuredConsoleContinuesOnQuestionAndStopsOnHold() throws Exception {
        var output = new StringWriter();
        GeminiChat.run("test", GeminiStructuredOffline.gateway(), "SCRIPTED_OFFLINE",
                new BufferedReader(new StringReader("요금제를 알려주세요\nC-100\n/exit\n")), new PrintWriter(output), true);
        assertTrue(output.toString().contains("ASK_CUSTOMER_ID"));
        assertTrue(output.toString().contains("SHOW_ACCOUNT"));
        assertTrue(output.toString().contains("대화를 종료합니다."));
        var count = new AtomicInteger();
        output = new StringWriter();
        GeminiChat.run("test", (m,h,c) -> {
            count.incrementAndGet();
            if (c.responseMimeType().isEmpty()) return GeminiToolLoop.offline("normal").generate(m,h,c);
            return GeminiStructuredOffline.answer(NORMAL.replace("basic", "premium"));
        }, "SCRIPTED_OFFLINE", new BufferedReader(new StringReader("조회\n다음 질문\n")), new PrintWriter(output), true);
        assertEquals(2, count.get());
        assertTrue(output.toString().contains("대화를 중단합니다: FACT_MISMATCH"));
    }

    @Test void offlineEntryPointSelectsStructuredFlowWithoutReadingLiveSettings() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var previous = System.out;
        try (var output = new PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(output);
            GeminiQuickstart.main(new String[]{"--tools", "--structured", "--offline"});
        } finally { System.setOut(previous); }
        var result = GeminiQuickstart.JSON.readTree(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("SHOW_ACCOUNT", result.path("next_action").asText());
        assertEquals("SCRIPTED_OFFLINE", result.path("mode").asText());
    }

    private static Map<String, Object> run(String text, GeminiToolLoop.Gateway gateway) {
        return GeminiToolLoop.run(text, "test", gateway, new ArrayList<>(), true);
    }

    private static List<Map<String, Object>> evidence() {
        return List.of(Map.of("arguments", Map.of("customer_id", "C-100", "fields", List.of("plan")),
                "result", Map.of("customer_id", "C-100", "plan", "basic")));
    }
}
