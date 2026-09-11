package lab.week06;

import com.google.genai.types.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GeminiToolLoopTest {
    @Test void normalAndMissingCustomerReturnActualResultsWithOriginalModelContent() {
        for (String customer : List.of("C-100", "C-404")) {
            var requests = new AtomicInteger();
            var first = call(customer);
            var original = first.candidates().orElseThrow().get(0).content().orElseThrow();
            var result = GeminiToolLoop.run(customer + " 고객의 요금제를 알려주세요.", "test-model",
                    (model, history, config) -> {
                        assertEquals("test-model", model);
                        assertEquals(GeminiToolLoop.TOOL, config.tools().orElseThrow().get(0));
                        if (requests.incrementAndGet() == 1) {
                            assertEquals(1, history.size());
                            assertEquals(customer + " 고객의 요금제를 알려주세요.",
                                    history.get(0).parts().orElseThrow().get(0).text().orElseThrow());
                            return first;
                        }
                        assertEquals(3, history.size());
                        // Includes the text part and original signature, not a reconstructed call.
                        assertSame(original, history.get(1));
                        var response = history.get(2).parts().orElseThrow().get(0)
                                .functionResponse().orElseThrow();
                        assertEquals("lookup-17", response.id().orElseThrow());
                        assertEquals(GeminiToolLoop.TOOL_NAME, response.name().orElseThrow());
                        assertEquals(customer.equals("C-100")
                                ? Map.of("customer_id", "C-100", "plan", "basic")
                                : Map.of("error", "CUSTOMER_NOT_FOUND"), response.response().orElseThrow());
                        return answer(customer.equals("C-100") ? "basic 요금제입니다." : "고객 번호를 확인해 주세요.");
                    });
            assertEquals(2, requests.get());
            assertEquals("MODEL_RESPONSE", result.get("status"));
            assertEquals(1, ((List<?>) result.get("tool_results")).size());
            assertEquals(customer.equals("C-100") ? "basic 요금제입니다." : "고객 번호를 확인해 주세요.", result.get("answer"));
        }
    }

    @Test void validatesFunctionAndArgumentsBeforeLookup() {
        assertEquals(Map.of("error", "UNKNOWN_TOOL"), GeminiToolLoop.executeCall("delete_customer",
                Map.of("customer_id", "C-100", "fields", List.of("plan"))));
        for (Map<String, Object> args : List.<Map<String, Object>>of(
                Map.of(), Map.of("customer_id", "C-100"),
                Map.of("customer_id", 100, "fields", List.of("plan")),
                Map.of("customer_id", " ", "fields", List.of("plan")),
                Map.of("customer_id", "C-100", "fields", List.of("plan"), "extra", true),
                Map.of("customer_id", "C-100", "fields", "plan"),
                Map.of("customer_id", "C-100", "fields", List.of()),
                Map.of("customer_id", "C-100", "fields", List.of("plan", "email")),
                Map.of("customer_id", "C-100", "fields", List.of(1)),
                Map.of("customer_id", "C-100", "fields", Arrays.asList("plan", null)))) {
            assertEquals(Map.of("error", "INVALID_ARGUMENTS"), GeminiToolLoop.executeCall(GeminiToolLoop.TOOL_NAME, args));
        }
        assertEquals(Map.of("error", "INVALID_ARGUMENTS"), GeminiToolLoop.executeCall(GeminiToolLoop.TOOL_NAME, null));
    }

    @Test void eachRequestedFieldSetIsProjectedBeforeTheNextModelRequest() throws Exception {
        var prompts = Map.of("normal", "C-100 고객의 요금제를 알려주세요.",
                "status", "C-100 고객의 계정 상태를 알려주세요.",
                "both", "C-100 고객의 요금제와 계정 상태를 알려주세요.");
        var expected = Map.of(
                "normal", Map.of("customer_id", "C-100", "plan", "basic"),
                "status", Map.of("customer_id", "C-100", "status", "active"),
                "both", Map.of("customer_id", "C-100", "plan", "basic", "status", "active"));
        var expectedFields = Map.of("normal", List.of("plan"), "status", List.of("status"),
                "both", List.of("plan", "status"));
        for (String scenario : List.of("normal", "status", "both")) {
            var delegate = GeminiToolLoop.offline(scenario);
            var requests = new AtomicInteger();
            var result = GeminiToolLoop.run(prompts.get(scenario), "test", (model, history, config) -> {
                if (requests.incrementAndGet() == 2) {
                    var call = history.get(1).parts().orElseThrow().get(0).functionCall().orElseThrow();
                    assertEquals(expectedFields.get(scenario), call.args().orElseThrow().get("fields"));
                    var response = history.get(2).parts().orElseThrow().get(0).functionResponse().orElseThrow();
                    assertEquals(call.id(), response.id());
                    assertEquals(expected.get(scenario), response.response().orElseThrow());
                }
                return delegate.generate(model, history, config);
            });
            assertEquals(2, requests.get());
            assertEquals("MODEL_RESPONSE", result.get("status"));
            var output = GeminiQuickstart.JSON.valueToTree(result);
            assertEquals(GeminiQuickstart.JSON.valueToTree(expected.get(scenario)),
                    output.path("tool_results").path(0).path("result"));
        }
        assertEquals(Map.of("customer_id", "C-100", "plan", "basic", "status", "active"),
                CustomerDirectory.lookup("C-100"));
        var schema = GeminiQuickstart.JSON.readTree(GeminiToolLoop.TOOL.toJson())
                .path("functionDeclarations").path(0).path("parameters");
        var required = new HashSet<String>();
        schema.path("required").forEach(item -> required.add(item.asText()));
        assertEquals(Set.of("customer_id", "fields"), required);
        var fieldsSchema = schema.path("properties").path("fields");
        assertEquals(1, fieldsSchema.path("minItems").asInt());
        assertEquals(GeminiQuickstart.JSON.valueToTree(List.of("plan", "status")),
                fieldsSchema.path("items").path("enum"));
    }

    @Test void projectionPreservesErrorsAndDoesNotMutateBusinessData() {
        var expected = Map.of("customer_id", "C-100", "plan", "basic");
        var result = GeminiToolLoop.executeCall(GeminiToolLoop.TOOL_NAME,
                Map.of("customer_id", "C-100", "fields", List.of("plan", "plan")));
        assertEquals(expected, result);
        result.put("plan", "changed locally");
        assertEquals("basic", CustomerDirectory.lookup("C-100").get("plan"));
        for (var fields : List.of(List.of("plan"), List.of("status"), List.of("plan", "status"))) {
            assertEquals(Map.of("error", "CUSTOMER_NOT_FOUND"), GeminiToolLoop.executeCall(
                    GeminiToolLoop.TOOL_NAME, Map.of("customer_id", "C-404", "fields", fields)));
        }
    }

    @Test void invalidArgumentsAreReturnedToModelAsAnError() {
        var requests = new AtomicInteger();
        var result = GeminiToolLoop.run("조회", "test", (model, history, config) -> {
            if (requests.incrementAndGet() == 1) return GenerateContentResponse.fromJson("""
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{
                "functionCall":{"name":"get_customer_context","args":{"customer_id":"C-100","fields":["email"]}}}]}}]}
                """);
            var response = history.get(2).parts().orElseThrow().get(0).functionResponse().orElseThrow();
            assertTrue(response.id().isEmpty());
            assertEquals(Map.of("error", "INVALID_ARGUMENTS"), response.response().orElseThrow());
            return answer("조회 항목을 확인해 주세요.");
        });
        assertEquals("MODEL_RESPONSE", result.get("status"));
    }

    @Test void repeatedCallsStopBeforeThirdExecution() {
        var result = GeminiToolLoop.run("조회", "test", GeminiToolLoop.offline("repeat"));
        assertEquals("STOPPED", result.get("status"));
        assertEquals(3, result.get("model_requests"));
        assertEquals(2, ((List<?>) result.get("tool_results")).size());
    }

    @Test void missingIdQuestionEndsWithoutLookup() {
        var result = GeminiToolLoop.run("요금제를 알려주세요", "test", (m, h, c) -> answer("고객 번호를 알려주세요."));
        assertEquals("MODEL_RESPONSE", result.get("status"));
        assertEquals(1, result.get("model_requests"));
        assertTrue(((List<?>) result.get("tool_results")).isEmpty());
    }

    @Test void multipleCallsIncompleteBlockedAndEmptyResponsesDoNotExecute() {
        String normal = call("C-100").toJson();
        for (var entry : Map.of(
                normal.replace("STOP", "MAX_TOKENS"), "INCOMPLETE",
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "REFUSED",
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"role\":\"model\",\"parts\":[]}}]}", "INVALID_OUTPUT"
                ).entrySet()) {
            var result = GeminiToolLoop.run("조회", "test", (m, h, c) -> GenerateContentResponse.fromJson(entry.getKey()));
            assertEquals(entry.getValue(), result.get("status"));
            assertTrue(((List<?>) result.get("tool_results")).isEmpty());
        }
        var content = call("C-100").candidates().orElseThrow().get(0).content().orElseThrow();
        var part = content.parts().orElseThrow().get(1);
        var multiple = GenerateContentResponse.builder().candidates(Candidate.builder().finishReason("STOP")
                .content(Content.builder().role("model").parts(part, part).build()).build()).build();
        var result = GeminiToolLoop.run("조회", "test", (m, h, c) -> multiple);
        assertEquals("INVALID_OUTPUT", result.get("status"));
        assertTrue(((List<?>) result.get("tool_results")).isEmpty());
    }

    @Test void failureAfterLookupPreservesLookupEvidenceWithoutFabricatingAnswer() {
        var requests = new AtomicInteger();
        var result = GeminiToolLoop.run("조회", "test", (m, h, c) -> {
            if (requests.incrementAndGet() == 1) return call("C-100");
            throw new IllegalStateException("private diagnostic");
        });
        assertEquals("PROVIDER_ERROR", result.get("status"));
        assertEquals(2, result.get("model_requests"));
        assertEquals(1, ((List<?>) result.get("tool_results")).size());
        assertEquals("", result.get("answer"));
        assertFalse(result.toString().contains("private diagnostic"));
    }

    private static GenerateContentResponse call(String id) {
        return GenerateContentResponse.fromJson("""
            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
            {"text":"고객 정보를 확인하겠습니다."},
            {"functionCall":{"id":"lookup-17","name":"get_customer_context","args":{"customer_id":"%s","fields":["plan"]}},
            "thoughtSignature":"dGVzdA=="}]}}]}
            """.formatted(id));
    }

    private static GenerateContentResponse answer(String text) {
        return GenerateContentResponse.builder().candidates(Candidate.builder().finishReason("STOP")
                .content(Content.builder().role("model").parts(Part.fromText(text)).build()).build()).build();
    }
}
