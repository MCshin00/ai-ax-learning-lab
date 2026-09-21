package lab.week06;

import com.google.genai.types.*;
import com.google.genai.errors.ApiException;
import java.util.*;

/** The application validates requested fields and returns only selected customer values. */
final class GeminiToolLoop {
    static final String TOOL_NAME = CustomerTool.TOOL_NAME;
    static final int MAX_REQUESTS = CustomerTool.MAX_REQUESTS;
    static final Set<String> ALLOWED_FIELDS = CustomerTool.ALLOWED_FIELDS;
    static final String DEFAULT_TEXT = CustomerTool.DEFAULT_TEXT;
    static final String INSTRUCTIONS = CustomerTool.INSTRUCTIONS;
    static final Tool TOOL = Tool.builder().functionDeclarations(FunctionDeclaration.fromJson("""
        {"name":"get_customer_context","description":"고객의 요금제·계정 상태 중 요청한 항목만 반환합니다.",
         "parameters":{"type":"OBJECT","properties":{"customer_id":{"type":"STRING",
         "description":"사용자가 제공한 고객 번호"},"fields":{"type":"ARRAY","minItems":1,
         "description":"질문한 조회 항목. 요금제는 plan, 계정 상태는 status, 둘 다면 두 항목.",
         "items":{"type":"STRING","enum":["plan","status"]}}},"required":["customer_id","fields"]}}
        """)).build();

    @FunctionalInterface
    interface Gateway {
        GenerateContentResponse generate(String model, List<Content> history, GenerateContentConfig config);
    }

    static GenerateContentConfig requestConfig() {
        return GeminiQuickstart.requestConfig().toBuilder()
                .systemInstruction(Content.fromParts(Part.fromText(INSTRUCTIONS)))
                .tools(TOOL).build();
    }

    static Map<String, Object> executeCall(String name, Map<String, Object> args) {
        return CustomerTool.executeCall(name, args);
    }

    static Map<String, Object> run(String text, String model, Gateway gateway) {
        return run(text, model, gateway, new ArrayList<>());
    }

    /** The caller owns this conversation; the request budget belongs to this input. */
    static Map<String, Object> run(String text, String model, Gateway gateway, List<Content> history) {
        return run(text, model, gateway, history, false);
    }

    static Map<String, Object> run(String text, String model, Gateway gateway,
                                   List<Content> history, boolean structured) {
        var result = new LinkedHashMap<String, Object>();
        var toolResults = new ArrayList<Map<String, Object>>();
        var turns = new ArrayList<Map<String, Object>>();
        history.add(Content.builder().role("user").parts(Part.fromText(text)).build());
        result.put("request", Map.of("model", model, "input", text,
                "instructions", INSTRUCTIONS, "tools", List.of(TOOL_NAME), "max_model_requests", MAX_REQUESTS,
                "context_messages", contextMessages(history)));
        result.put("status", "NOT_VERIFIED");
        result.put("answer", "");
        result.put("model_requests", 0);
        result.put("tool_results", toolResults);
        result.put("turns", turns);
        if (structured) {
            result.put("next_action", "HOLD");
            result.put("response_schema", GeminiStructuredAnswer.SCHEMA);
        }
        boolean finalPhase = false;
        for (int index = 1; index <= MAX_REQUESTS; index++) {
            result.put("model_requests", index);
            GenerateContentResponse response;
            try {
                // Immutable snapshot: a test double sees exactly what this request sent.
                response = gateway.generate(model, List.copyOf(history),
                        finalPhase ? GeminiStructuredAnswer.config(toolResults) : requestConfig());
            } catch (RuntimeException error) {
                result.put("status", "PROVIDER_ERROR");
                result.put("answer", "");
                result.put("message", "API 응답을 받지 못했습니다. 연결·모델 설정·할당량을 확인하세요.");
                if (error instanceof ApiException apiError) result.put("http_status", apiError.code());
                return result;
            }
            try {
                var body = GeminiQuickstart.JSON.readTree(response.toJson());
                var candidateJson = body.path("candidates").path(0);
                String finish = candidateJson.path("finishReason").asText("UNAVAILABLE");
                String block = body.path("promptFeedback").path("blockReason")
                        .asText("BLOCK_REASON_UNSPECIFIED");
                var turn = new LinkedHashMap<String, Object>();
                turn.put("request_number", index);
                if (structured) turn.put("phase", finalPhase ? "STRUCTURED_ANSWER" : "TOOL_SELECTION");
                turn.put("input_contents", history.size());
                turn.put("finish_reason", finish);
                turn.put("usage_status", body.hasNonNull("usageMetadata") ? "REPORTED" : "UNAVAILABLE");
                turn.put("usage", body.hasNonNull("usageMetadata") ? body.get("usageMetadata") : null);
                turns.add(turn);
                if (!block.equals("BLOCK_REASON_UNSPECIFIED")
                        || Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT",
                                "SPII", "IMAGE_SAFETY").contains(finish)) {
                    result.put("status", "REFUSED");
                    return result;
                }
                if (!"STOP".equals(finish)) {
                    result.put("status", "INCOMPLETE");
                    return result;
                }
                Content content = response.candidates().orElseThrow().get(0).content().orElseThrow();
                var parts = content.parts().orElse(List.of());
                var calls = parts.stream().flatMap(part -> part.functionCall().stream()).toList();
                if (structured && finalPhase) {
                    if (!calls.isEmpty()) {
                        result.putAll(GeminiStructuredAnswer.hold("INVALID_OUTPUT"));
                        return result;
                    }
                    String raw = parts.stream().filter(part -> !part.thought().orElse(false))
                            .flatMap(part -> part.text().stream()).reduce("", String::concat);
                    result.put("structured_response", raw);
                    result.putAll(GeminiStructuredAnswer.consume(raw, toolResults));
                    if ("MODEL_RESPONSE".equals(result.get("status"))) history.add(content);
                    return result;
                }
                if (calls.isEmpty()) {
                    String answer = parts.stream().filter(part -> !part.thought().orElse(false))
                            .flatMap(part -> part.text().stream()).reduce("", String::concat);
                    if (structured && !answer.isBlank()) {
                        // The unstructured draft is not presented as an accepted final answer.
                        finalPhase = true;
                        continue;
                    }
                    result.put("answer", answer);
                    result.put("status", answer.isBlank() ? "INVALID_OUTPUT" : "MODEL_RESPONSE");
                    // Clarifications and final answers must survive the next user input too.
                    if (!answer.isBlank()) history.add(content);
                    return result;
                }
                if (calls.size() != 1) {
                    result.put("status", "INVALID_OUTPUT");
                    result.put("message", "한 응답에 여러 함수 호출이 있어 실행하지 않았습니다.");
                    return result;
                }
                if (index == MAX_REQUESTS) {
                    result.put("status", "STOPPED");
                    result.put("message", "모델 요청 한도에 도달해 추가 함수를 실행하지 않습니다.");
                    return result;
                }
                var call = calls.get(0);
                String name = call.name().orElse("");
                if (name.isBlank()) {
                    result.put("status", "INVALID_OUTPUT");
                    return result;
                }
                Map<String, Object> args = call.args().orElse(null);
                var value = executeCall(name, args);
                var functionResponse = FunctionResponse.builder().name(name).response(value);
                call.id().ifPresent(functionResponse::id);
                // Preserve every model part, including thoughtSignature, without reconstruction.
                history.add(content);
                history.add(Content.builder().role("user")
                        .parts(Part.builder().functionResponse(functionResponse.build()).build()).build());
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("name", name);
                evidence.put("call_id", call.id().orElse(null));
                evidence.put("arguments", args);
                evidence.put("result", value);
                toolResults.add(evidence);
                if (structured) {
                    if (value.containsKey("error") && !"CUSTOMER_NOT_FOUND".equals(value.get("error"))) {
                        result.putAll(GeminiStructuredAnswer.hold("TOOL_ERROR"));
                        return result;
                    }
                    finalPhase = true;
                }
            } catch (Exception error) {
                result.put("status", "INVALID_OUTPUT");
                result.put("message", "응답 형식을 해석하지 못했습니다.");
                return result;
            }
        }
        throw new IllegalStateException("Request loop must return at its limit");
    }

    /** Readable request evidence, not the full SDK payload used for transmission. */
    private static List<Map<String, Object>> contextMessages(List<Content> history) {
        return history.stream().map(content -> {
            var message = new LinkedHashMap<String, Object>();
            message.put("role", content.role().orElse(""));
            var parts = content.parts().orElse(List.of());
            message.put("text", parts.stream().filter(part -> !part.thought().orElse(false))
                    .flatMap(part -> part.text().stream()).reduce("", String::concat));
            var calls = parts.stream().flatMap(part -> part.functionCall().stream())
                    .map(call -> call.name().orElse("")).toList();
            var responses = parts.stream().flatMap(part -> part.functionResponse().stream())
                    .map(response -> response.name().orElse("")).toList();
            if (!calls.isEmpty()) message.put("function_calls", calls);
            if (!responses.isEmpty()) message.put("function_responses", responses);
            return (Map<String, Object>) message;
        }).toList();
    }

    /** Model responses are fixed; argument validation and customer lookup are real. */
    static Gateway offline(String scenario) {
        String id = switch (scenario) {
            case "normal", "status", "both", "repeat" -> "C-100";
            case "unknown" -> "C-404";
            default -> throw new IllegalArgumentException("Unsupported offline case");
        };
        String fieldsJson = switch (scenario) {
            case "status" -> "[\"status\"]";
            case "both" -> "[\"plan\",\"status\"]";
            default -> "[\"plan\"]";
        };
        return (model, history, config) -> {
            if (history.size() == 1 || "repeat".equals(scenario)) {
                return GenerateContentResponse.fromJson("""
                    {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{
                    "functionCall":{"id":"offline-%d","name":"get_customer_context",
                    "args":{"customer_id":"%s","fields":%s}},"thoughtSignature":"b2ZmbGluZQ=="}]}}]}
                    """.formatted(history.size(), id, fieldsJson));
            }
            var value = history.get(history.size() - 1).parts().orElseThrow().get(0)
                    .functionResponse().orElseThrow().response().orElseThrow();
            var facts = new ArrayList<String>();
            if (value.containsKey("plan")) facts.add("요금제는 " + value.get("plan"));
            if (value.containsKey("status")) facts.add("계정 상태는 " + value.get("status"));
            String answer = "CUSTOMER_NOT_FOUND".equals(value.get("error"))
                    ? "고객을 찾을 수 없습니다. 고객 번호를 확인해 주세요."
                    : value.get("customer_id") + " 고객의 " + String.join(", ", facts) + "입니다.";
            return GenerateContentResponse.builder().candidates(Candidate.builder().finishReason("STOP")
                    .content(Content.builder().role("model").parts(Part.fromText(answer)).build()).build()).build();
        };
    }
}
