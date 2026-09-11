package lab.week06;

import com.google.genai.types.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GeminiChatTest {
    @Test void clarificationAndOriginalContentReachTheFollowupRequest() throws Exception {
        var delegate = GeminiChat.offline();
        var requests = new ArrayList<List<Content>>();
        var responses = new ArrayList<Content>();
        String output = chat("요금제를 알려주세요\nC-100\n/exit\n", (model, history, config) -> {
            requests.add(history);
            var response = delegate.generate(model, history, config);
            responses.add(response.candidates().orElseThrow().get(0).content().orElseThrow());
            return response;
        });
        assertEquals(List.of(1, 3, 5), requests.stream().map(List::size).toList());
        assertEquals("요금제를 알려주세요", text(requests.get(1).get(0)));
        assertSame(responses.get(0), requests.get(1).get(1));
        assertTrue(requests.get(1).get(1).parts().orElseThrow().get(0).thoughtSignature().isPresent());
        assertEquals("C-100", text(requests.get(1).get(2)));
        assertSame(responses.get(1), requests.get(2).get(3));
        var call = requests.get(2).get(3).parts().orElseThrow().get(0).functionCall().orElseThrow();
        var result = requests.get(2).get(4).parts().orElseThrow().get(0).functionResponse().orElseThrow();
        assertEquals(call.id(), result.id());
        assertEquals(Map.of("customer_id", "C-100", "plan", "basic"), result.response().orElseThrow());
        assertTrue(output.contains("C-100 고객의 요금제는 basic입니다."));
        assertTrue(output.contains("\"model_requests\" : 1"));
        assertTrue(output.contains("\"model_requests\" : 2"));
        assertTrue(output.contains("\"context_messages\""));
        assertFalse(output.contains("thoughtSignature"));
    }

    @Test void eachInputGetsFreshBudgetAndKeepsEarlierFinalAnswer() {
        var history = new ArrayList<Content>();
        var requests = new AtomicInteger();
        var finalResponses = new ArrayList<Content>();
        GeminiToolLoop.Gateway gateway = (model, sent, config) -> {
            int number = requests.incrementAndGet();
            if (number == 3) {
                assertEquals(5, sent.size());
                assertSame(finalResponses.get(0), sent.get(3));
                assertEquals("그 고객의 계정 상태는?", text(sent.get(4)));
            }
            if (number % 2 == 1) return call(number == 1 ? "plan" : "status");
            var value = sent.get(sent.size() - 1).parts().orElseThrow().get(0)
                    .functionResponse().orElseThrow().response().orElseThrow();
            assertEquals(number == 2 ? Map.of("customer_id", "C-100", "plan", "basic")
                    : Map.of("customer_id", "C-100", "status", "active"), value);
            var response = answer(number == 2 ? "basic" : "active");
            finalResponses.add(response.candidates().orElseThrow().get(0).content().orElseThrow());
            return response;
        };
        var first = GeminiToolLoop.run(GeminiToolLoop.DEFAULT_TEXT, "test", gateway, history);
        var second = GeminiToolLoop.run("그 고객의 계정 상태는?", "test", gateway, history);
        assertEquals(4, requests.get());
        assertEquals(2, first.get("model_requests"));
        assertEquals(2, second.get("model_requests"));
        assertEquals("MODEL_RESPONSE", second.get("status"));
        assertEquals("active", second.get("answer"));
        assertEquals(8, history.size());
        assertSame(finalResponses.get(1), history.get(7));
    }

    @Test void repeatedCallsEndConversationWithoutProcessingQueuedInput() throws Exception {
        var requests = new AtomicInteger();
        var lastHistory = new ArrayList<Content>();
        String output = chat("조회\n다음 질문\n", (model, history, config) -> {
            requests.incrementAndGet();
            lastHistory.clear();
            lastHistory.addAll(history);
            return call("plan");
        });
        assertEquals(3, requests.get());
        assertEquals(5, lastHistory.size());
        assertEquals(2, lastHistory.stream().flatMap(item -> item.parts().orElseThrow().stream())
                .filter(part -> part.functionResponse().isPresent()).count());
        assertTrue(output.contains("대화를 중단합니다: STOPPED"));
        assertFalse(output.contains("다음 질문"));
    }

    @Test void unsuccessfulResponsesAndProviderErrorEndConversation() throws Exception {
        for (var entry : Map.of(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "REFUSED",
                answer("중간 응답").toJson().replace("STOP", "MAX_TOKENS"), "INCOMPLETE",
                answer("").toJson(), "INVALID_OUTPUT").entrySet()) {
            var requests = new AtomicInteger();
            String output = chat("질문\n다음 질문\n", (m, h, c) -> {
                requests.incrementAndGet();
                return GenerateContentResponse.fromJson(entry.getKey());
            });
            assertEquals(1, requests.get());
            assertTrue(output.contains("대화를 중단합니다: " + entry.getValue()));
        }
        var requests = new AtomicInteger();
        String output = chat("질문\n다음 질문\n", (m, h, c) -> {
            if (requests.incrementAndGet() == 1) return call("plan");
            throw new IllegalStateException("private diagnostic");
        });
        assertEquals(2, requests.get());
        assertTrue(output.contains("대화를 중단합니다: PROVIDER_ERROR"));
        assertFalse(output.contains("private diagnostic"));
        assertFalse(output.contains("다음 질문"));
    }

    @Test void blankExitAndEndOfInputDoNotRequestAModel() throws Exception {
        for (String input : List.of("\n  \n/exit\n질문\n", "", "  \n")) {
            String output = chat(input, (m, h, c) -> {
                fail("No model request expected");
                return null;
            });
            assertTrue(output.contains("대화를 종료합니다."));
        }
    }

    @Test void singleQuestionStillWorksWithChatAndStandaloneEntryPoints() throws Exception {
        String output = chat(GeminiToolLoop.DEFAULT_TEXT + "\n/exit\n", GeminiChat.offline());
        assertTrue(output.contains("C-100 고객의 요금제는 basic입니다."));
        var result = GeminiToolLoop.run(GeminiToolLoop.DEFAULT_TEXT, "test", GeminiToolLoop.offline("normal"));
        assertEquals("MODEL_RESPONSE", result.get("status"));
        assertEquals(2, result.get("model_requests"));
        assertEquals("C-100 고객의 요금제는 basic입니다.", result.get("answer"));
    }

    private static String chat(String input, GeminiToolLoop.Gateway gateway) throws Exception {
        var output = new StringWriter();
        GeminiChat.run("test", gateway, "SCRIPTED_OFFLINE",
                new BufferedReader(new StringReader(input)), new PrintWriter(output));
        return output.toString();
    }

    private static String text(Content content) {
        return content.parts().orElseThrow().get(0).text().orElseThrow();
    }

    private static GenerateContentResponse call(String field) {
        return GenerateContentResponse.fromJson("""
            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
            {"functionCall":{"id":"test-call","name":"get_customer_context",
            "args":{"customer_id":"C-100","fields":["%s"]}},"thoughtSignature":"dGVzdA=="}]}}]}
            """.formatted(field));
    }

    private static GenerateContentResponse answer(String text) {
        return GenerateContentResponse.builder().candidates(Candidate.builder().finishReason("STOP")
                .content(Content.builder().role("model").parts(Part.fromText(text)).build()).build()).build();
    }
}
