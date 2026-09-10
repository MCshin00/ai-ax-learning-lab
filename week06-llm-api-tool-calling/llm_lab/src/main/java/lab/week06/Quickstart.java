package lab.week06;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.*;
import java.time.Duration;
import java.util.*;

/** Bounded model/tool loop. The application owns execution and termination. */
public final class Quickstart {
    static final ObjectMapper JSON = ObjectMappers.jsonMapper();
    static final ObjectMapper ARGUMENTS = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static final Map<String, Map<String, Object>> CUSTOMERS = CustomerDirectory.CUSTOMERS;
    public static final String INSTRUCTIONS = "고객 ID가 없으면 먼저 물어보세요. 고객 정보는 get_customer_context로 확인하세요. "
            + "도구 오류나 없는 고객을 성공으로 설명하지 마세요. 조회만 가능하며 변경은 하지 않습니다.";
    public static final FunctionTool TOOL = FunctionTool.builder()
            .name("get_customer_context").description("고객 ID로 요금제와 계정 상태를 조회합니다.").strict(true)
            .parameters(FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(Map.of("customer_id", Map.of("type", "string"))))
                    .putAdditionalProperty("required", JsonValue.from(List.of("customer_id")))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build()).build();

    public record Usage(long inputTokens, long cachedInputTokens, long outputTokens, long reasoningTokens) {}
    public record Turn(String status, String text, List<ResponseOutputItem> output, boolean refused, Usage usage) {
        public Turn(String status, String text, List<ResponseOutputItem> output, boolean refused) {
            this(status, text, output, refused, null);
        }
    }
    @FunctionalInterface public interface Gateway { Turn create(ResponseCreateParams request); }

    public static Map<String, Object> getCustomerContext(String id) {
        return CustomerDirectory.lookup(id);
    }

    public static Map<String, Object> executeCall(String name, String arguments) {
        if (!TOOL.name().equals(name)) return Map.of("error", "UNKNOWN_TOOL");
        JsonNode data;
        try { data = ARGUMENTS.readTree(arguments); }
        catch (Exception e) { return Map.of("error", "INVALID_ARGUMENTS"); }
        if (data == null || !data.isObject() || data.size() != 1 || !data.has("customer_id"))
            return Map.of("error", "INVALID_ARGUMENTS");
        if (!data.get("customer_id").isTextual()) return Map.of("error", "INVALID_CUSTOMER_ID");
        return getCustomerContext(data.get("customer_id").textValue());
    }

    public static Map<String, Object> run(String text, Gateway client, String model, boolean plain) {
        return run(text, client, model, plain, 3);
    }

    public static Map<String, Object> run(String text, Gateway client, String model, boolean plain, int maxRequests) {
        if (maxRequests < 1 || maxRequests > 5) throw new IllegalArgumentException("maxRequests must be 1..5");
        var history = new ArrayList<ResponseInputItem>();
        history.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER).content(text).build()));
        var toolResults = new ArrayList<Map<String, Object>>();
        var turns = new ArrayList<Map<String, Object>>();
        for (int index = 1; index <= maxRequests; index++) {
            var request = ResponseCreateParams.builder().model(model).inputOfResponse(history)
                .instructions(plain ? "사용자의 요청에 한국어로 간결하게 답하세요." : INSTRUCTIONS)
                .maxOutputTokens(700).store(false);
            if (!plain) request.addTool(TOOL).parallelToolCalls(false)
                .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
            Turn turn;
            try { turn = client.create(request.build()); }
            catch (RuntimeException e) {
                turns.add(Map.of("request", index, "status", "PROVIDER_ERROR", "usage_status", "UNAVAILABLE"));
                return result("PROVIDER_ERROR", "API 호출 결과를 받지 못했습니다.", toolResults, turns);
            }
            var summary = new LinkedHashMap<String, Object>();
            summary.put("request", index); summary.put("status", turn.status());
            summary.put("usage_status", turn.usage() == null ? "UNAVAILABLE" : "REPORTED");
            summary.put("usage", turn.usage());
            turns.add(summary);
            if (!turn.status().equals("completed")) return result("INCOMPLETE", turn.text(), toolResults, turns);
            if (turn.refused()) return result("REFUSED", turn.text(), toolResults, turns);
            var calls = turn.output().stream().flatMap(item -> item.functionCall().stream()).toList();
            if (calls.isEmpty()) return result(turn.text().isBlank() ? "INVALID_OUTPUT" : "MODEL_RESPONSE", turn.text(), toolResults, turns);
            if (calls.size() != 1 || plain)
                return result("STOPPED", "이 루프는 응답당 도구 호출 하나를 순서대로 처리합니다.", toolResults, turns);
            if (index == maxRequests)
                return result("STOPPED", "모델 요청 한도에 도달해 추가 함수를 실행하지 않습니다.", toolResults, turns);
            var call = calls.get(0);
            var toolResult = executeCall(call.name(), call.arguments());
            toolResults.add(Map.of("call_id", call.callId(), "name", call.name(), "result", toolResult));
            // Preserve all items, including reasoning. Errors also return to the matching call.
            turn.output().forEach(item -> history.add(JSON.convertValue(item, ResponseInputItem.class)));
            history.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                    .callId(call.callId()).output(json(toolResult)).build()));
        }
        throw new IllegalStateException("Unreachable loop end");
    }

    private static Map<String, Object> result(String status, String answer, List<?> tools, List<?> turns) {
        return new LinkedHashMap<>(Map.of("status", status, "answer", answer, "tool_results", tools,
                "model_requests", turns.size(), "turns", turns));
    }

    public static Gateway liveClient() {
        if (!"1".equals(System.getenv("AI_AX_LIVE")) || System.getenv("OPENAI_API_KEY") == null
                || System.getenv("OPENAI_MODEL") == null)
            throw new IllegalStateException("Set AI_AX_LIVE=1, OPENAI_API_KEY and OPENAI_MODEL before a billed call.");
        OpenAIClient client = OpenAIOkHttpClient.builder().fromEnv().timeout(Duration.ofSeconds(20)).maxRetries(0).build();
        return request -> {
            Response response = client.responses().create(request);
            String answer = response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text).reduce("", String::concat);
            boolean refused = response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).anyMatch(content -> content.refusal().isPresent());
            Usage usage = response.usage().map(u -> new Usage(u.inputTokens(), u.inputTokensDetails().cachedTokens(),
                    u.outputTokens(), u.outputTokensDetails().reasoningTokens())).orElse(null);
            return new Turn(response.status().map(Object::toString).orElse("unknown"), answer, response.output(), refused, usage);
        };
    }

    /** Scripts the model boundary; requests still pass through the same application logic. */
    public static final class OfflineClient implements Gateway {
        public final List<ResponseCreateParams> requests = new ArrayList<>();
        private final String scenario;
        public OfflineClient(String scenario) { this.scenario = scenario; }
        public Turn create(ResponseCreateParams request) {
            requests.add(request);
            if (requests.size() == 2 && !scenario.equals("repeat")) {
                var input = request.input().orElseThrow().asResponse();
                var output = input.get(input.size() - 1).asFunctionCallOutput().output().asString();
                try {
                    var data = JSON.readTree(output);
                    if (data.has("error")) return new Turn("completed", "조회 결과: " + data.get("error").asText()
                            + ". 고객 ID를 확인해 주세요.", List.of(), false);
                    return new Turn("completed", data.get("customer_id").asText() + " 고객의 요금제는 "
                            + data.get("plan").asText() + "입니다.", List.of(), false);
                } catch (Exception e) { throw new IllegalStateException(e); }
            }
            if (scenario.equals("missing")) return new Turn("completed", "고객 ID를 알려주세요.", List.of(), false);
            if (request.tools().isEmpty()) return new Turn("completed", "이 문장은 고정된 오프라인 예시입니다.", List.of(), false);
            String id = scenario.equals("unknown") ? "C-404" : "C-100";
            var call = ResponseFunctionToolCall.builder().name(TOOL.name()).callId("offline-" + requests.size())
                    .arguments(json(Map.of("customer_id", id))).build();
            return new Turn("completed", "", List.of(ResponseOutputItem.ofFunctionCall(call)), false);
        }
    }

    public static String json(Object value) {
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot serialize result", e); }
    }
    public static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length; i++) if (args[i].equals(name)) {
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing value: " + name);
            return args[i + 1];
        }
        return fallback;
    }
    public static void main(String[] args) {
        boolean offline = Arrays.asList(args).contains("--offline");
        String scenario = option(args, "--case", "normal");
        if (!Set.of("normal", "missing", "unknown", "repeat").contains(scenario)) throw new IllegalArgumentException("Unknown case");
        var result = run(option(args, "--text", "C-100 고객의 요금제를 알려주세요."),
                offline ? new OfflineClient(scenario) : liveClient(), offline ? "scripted-offline" : System.getenv("OPENAI_MODEL"),
                Arrays.asList(args).contains("--plain"));
        result.put("mode", offline ? "SCRIPTED_OFFLINE" : "LIVE");
        System.out.println(json(result));
    }
}
