package lab.week06;

import com.openai.models.responses.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCustomerAssistantTest {
    @Test void sharedProjectionAndOutputSchemaDriveNativeOpenAiRequests() {
        for (String text : List.of(CustomerTool.DEFAULT_TEXT, "C-100 고객의 계정 상태를 알려주세요.",
                "C-100 고객의 요금제와 계정 상태를 알려주세요.", "C-404 고객의 요금제를 알려주세요.")) {
            var requests = new ArrayList<ResponseCreateParams>();
            var fixture = OpenAiCustomerOffline.gateway();
            var result = run(text, request -> {
                requests.add(request);
                assertEquals("fixture-model", request.model().orElseThrow().asString());
                assertEquals(false, request.store().orElseThrow());
                if (requests.size() == 1) {
                    assertEquals(1, request.tools().orElseThrow().size());
                    assertEquals(false, request.parallelToolCalls().orElseThrow());
                    assertTrue(request.text().isEmpty());
                } else {
                    assertTrue(request.tools().isEmpty());
                    var schema = request.text().orElseThrow().format().orElseThrow().asJsonSchema();
                    assertTrue(schema.strict().orElseThrow());
                    assertEquals(Quickstart.JSON.valueToTree(CustomerAnswer.SCHEMA), Quickstart.JSON.valueToTree(schema.schema()));
                    var input = request.input().orElseThrow().asResponse();
                    assertEquals(input.get(1).asFunctionCall().callId(), input.get(2).asFunctionCallOutput().callId().orElseThrow());
                }
                return fixture.create(request);
            });
            assertEquals(2, requests.size());
            assertEquals(text.contains("C-404") ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT", result.get("next_action"));
            var facts = (Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("tool_results")).get(0)).get("result");
            var data = (Map<?, ?>) result.get("data");
            assertEquals(facts.get("plan"), data.get("plan"));
            assertEquals(facts.get("status"), data.get("account_status"));
        }
    }

    @Test void missingIdAndFollowupPreserveAcceptedOutputAndResetInputBudget() {
        var history = new ArrayList<ResponseInputItem>();
        var requests = new ArrayList<ResponseCreateParams>();
        var fixture = OpenAiCustomerOffline.gateway();
        Quickstart.Gateway gateway = request -> { requests.add(request); return fixture.create(request); };
        var first = OpenAiCustomerAssistant.run("요금제를 알려주세요", "fixture-model", gateway, history);
        var second = OpenAiCustomerAssistant.run("C-100", "fixture-model", gateway, history);
        assertEquals("ASK_CUSTOMER_ID", first.get("next_action"));
        assertEquals("SHOW_ACCOUNT", second.get("next_action"));
        assertEquals(2, first.get("model_requests"));
        assertEquals(2, second.get("model_requests"));
        assertTrue(((List<?>) first.get("tool_results")).isEmpty());
        var next = requests.get(2).input().orElseThrow().asResponse();
        assertEquals(3, next.size());
        assertEquals("offline-message-2", Quickstart.JSON.valueToTree(next.get(1)).path("id").asText());
        assertTrue(Quickstart.JSON.valueToTree(next.get(1)).toString().contains("needs_follow_up"));
        assertEquals(6, history.size());
        var third = OpenAiCustomerAssistant.run("C-100 고객의 계정 상태를 알려주세요.", "fixture-model", gateway, history);
        assertNull(((Map<?, ?>) third.get("data")).get("plan"));
        assertEquals("active", ((Map<?, ?>) third.get("data")).get("account_status"));
    }

    @Test void reasoningAndToolCallItemsArePreservedBeforeMatchingResult() {
        var fixture = OpenAiCustomerOffline.gateway();
        run(CustomerTool.DEFAULT_TEXT, request -> {
            if (request.text().isEmpty()) {
                var turn = fixture.create(request);
                var reasoning = ResponseReasoningItem.builder().id("reasoning-fixture").summary(List.of())
                        .encryptedContent("fixture-only").build();
                return new Quickstart.Turn("completed", "", List.of(ResponseOutputItem.ofReasoning(reasoning), turn.output().get(0)), false);
            }
            var input = request.input().orElseThrow().asResponse();
            assertEquals("fixture-only", input.get(1).asReasoning().encryptedContent().orElseThrow());
            assertEquals(input.get(2).asFunctionCall().callId(), input.get(3).asFunctionCallOutput().callId().orElseThrow());
            return fixture.create(request);
        });
    }

    @Test void finalFailuresHoldWithoutSavingRejectedAnswersOrExecutingMoreTools() {
        for (String scenario : List.of("wrong-plan", "invalid-json", "refused", "incomplete", "extra-call", "transport")) {
            var history = new ArrayList<ResponseInputItem>();
            var fixture = OpenAiCustomerOffline.gateway();
            var result = OpenAiCustomerAssistant.run(CustomerTool.DEFAULT_TEXT, "fixture-model", request -> {
                if (request.text().isEmpty()) return fixture.create(request);
                return switch (scenario) {
                    case "wrong-plan" -> OpenAiCustomerOffline.answer(fixture.create(request).text().replace("basic", "premium"), 2);
                    case "invalid-json" -> OpenAiCustomerOffline.answer("{} {}", 2);
                    case "refused" -> new Quickstart.Turn("completed", "declined", List.of(), true);
                    case "incomplete" -> new Quickstart.Turn("incomplete", "{", List.of(), false);
                    case "extra-call" -> call("get_customer_context", "{\"customer_id\":\"C-100\",\"fields\":[\"plan\"]}");
                    default -> throw new IllegalStateException("private diagnostic");
                };
            }, history);
            assertEquals(switch (scenario) {
                case "wrong-plan" -> "FACT_MISMATCH";
                case "refused" -> "REFUSED";
                case "incomplete" -> "INCOMPLETE";
                case "transport" -> "PROVIDER_ERROR";
                default -> "INVALID_OUTPUT";
            }, result.get("status"));
            assertEquals("HOLD", result.get("next_action"));
            assertEquals("", result.get("answer"));
            assertFalse(result.containsKey("data"));
            assertEquals(1, ((List<?>) result.get("tool_results")).size());
            assertEquals(3, history.size());
            assertFalse(result.toString().contains("private diagnostic"));
        }
    }

    @Test void malformedArgumentsAndUnknownToolsStopBeforeFinalModelRequest() {
        for (String raw : List.of("[]", "{}", "{} {}", "{\"customer_id\":\"C-100\",\"fields\":[\"email\"]}",
                "{\"customer_id\":\"C-100\",\"customer_id\":\"C-404\",\"fields\":[\"plan\"]}")) {
            var result = run("조회", request -> call(CustomerTool.TOOL_NAME, raw));
            assertEquals("TOOL_ERROR", result.get("status"));
            assertEquals(1, result.get("model_requests"));
        }
        assertEquals("TOOL_ERROR", run("조회", request -> call("delete_customer", "{}")).get("status"));
        var repeated = call(CustomerTool.TOOL_NAME, "{}");
        assertEquals("INVALID_OUTPUT", run("조회", request -> new Quickstart.Turn("completed", "",
                List.of(repeated.output().get(0), repeated.output().get(0)), false)).get("status"));
    }

    @Test void sdkResponseDecodingPreservesTextRefusalUsageAndOutput() throws Exception {
        var response = Quickstart.JSON.readValue("""
            {"id":"resp_fixture","created_at":0,"model":"fixture-model","object":"response","status":"completed",
             "output":[{"id":"msg_fixture","type":"message","role":"assistant","status":"completed",
             "content":[{"type":"output_text","text":"안내","annotations":[]}]}],
             "usage":{"input_tokens":12,"input_tokens_details":{"cached_tokens":0},
             "output_tokens":5,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":17}}
            """, Response.class);
        var turn = OpenAiCustomerAssistant.decode(response);
        assertEquals("안내", turn.text());
        assertEquals("completed", turn.status());
        assertFalse(turn.refused());
        assertEquals(new Quickstart.Usage(12, 0, 5, 0), turn.usage());
        assertEquals(response.output(), turn.output());
        var refusal = Quickstart.JSON.readValue("""
            {"id":"resp_fixture","created_at":0,"model":"fixture-model","object":"response","status":"completed",
             "output":[{"id":"msg_fixture","type":"message","role":"assistant","status":"completed",
             "content":[{"type":"refusal","refusal":"declined"}]}]}
            """, Response.class);
        assertTrue(OpenAiCustomerAssistant.decode(refusal).refused());
        assertNull(OpenAiCustomerAssistant.decode(refusal).usage());
    }

    @Test void consoleContinuesForQuestionAndStopsOnHold() throws Exception {
        var out = new StringWriter();
        OpenAiCustomerAssistant.chat("fixture-model", OpenAiCustomerOffline.gateway(), "SCRIPTED_OFFLINE",
                new BufferedReader(new StringReader("요금제를 알려주세요\nC-100\n/exit\n")), new PrintWriter(out));
        assertTrue(out.toString().contains("ASK_CUSTOMER_ID"));
        assertTrue(out.toString().contains("SHOW_ACCOUNT"));
        assertTrue(out.toString().contains("대화를 종료합니다."));
        out = new StringWriter();
        OpenAiCustomerAssistant.chat("fixture-model", request -> new Quickstart.Turn("incomplete", "{", List.of(), false),
                "SCRIPTED_OFFLINE", new BufferedReader(new StringReader("질문\n다음 질문\n")), new PrintWriter(out));
        assertTrue(out.toString().contains("대화를 중단합니다: INCOMPLETE"));
        assertFalse(out.toString().contains("다음 질문"));
    }

    @Test void offlineEntryPointUsesNoLiveSettingsAndArgumentsAreValidatedFirst() throws Exception {
        assertEquals(3, OpenAiCustomerAssistant.missingSettings(Map.of()).size());
        assertTrue(OpenAiCustomerAssistant.missingSettings(Map.of("OPENAI_API_KEY", "example-only",
                "OPENAI_MODEL", "fixture-model", "AI_AX_LIVE", "1")).isEmpty());
        assertEquals("SHOW_ACCOUNT", invoke("--offline", "--tools", "--structured").path("next_action").asText());
        assertEquals("INVALID_ARGUMENTS", invoke("--text").path("status").asText());
        assertEquals("INVALID_ARGUMENTS", invoke("--chat", "--text", "C-100").path("status").asText());
    }

    private static com.fasterxml.jackson.databind.JsonNode invoke(String... args) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var original = System.out;
        try (var out = new PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(out);
            OpenAiCustomerAssistant.main(args);
        } finally { System.setOut(original); }
        return Quickstart.JSON.readTree(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<String, Object> run(String text, Quickstart.Gateway gateway) {
        return OpenAiCustomerAssistant.run(text, "fixture-model", gateway, new ArrayList<>());
    }

    private static Quickstart.Turn call(String name, String args) {
        return new Quickstart.Turn("completed", "", List.of(ResponseOutputItem.ofFunctionCall(
                ResponseFunctionToolCall.builder().name(name).arguments(args).callId("fixture-call").build())), false);
    }
}
