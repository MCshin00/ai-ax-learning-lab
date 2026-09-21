package lab.week06;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** OpenAI Responses transport for the existing customer workflow. */
public final class OpenAiCustomerAssistant {
    private static final JsonMapper ARGUMENTS = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static final FunctionTool TOOL = FunctionTool.builder().name(CustomerTool.TOOL_NAME)
            .description("고객의 요금제·계정 상태 중 요청한 항목만 반환합니다.").strict(true)
            .parameters(FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "customer_id", Map.of("type", "string", "description", "사용자가 제공한 고객 번호"),
                    "fields", Map.of("type", "array", "minItems", 1,
                        "items", Map.of("type", "string", "enum", List.of("plan", "status"))))))
                .putAdditionalProperty("required", JsonValue.from(List.of("customer_id", "fields")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build()).build();

    static String json(Object value) {
        // Preserve explicit null business fields; the SDK mapper omits null map values.
        try { return ARGUMENTS.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("Cannot serialize customer result", error); }
    }

    static ResponseTextConfig format() {
        var schema = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        CustomerAnswer.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        return ResponseTextConfig.builder().format(ResponseFormatTextJsonSchemaConfig.builder()
                .name("customer_answer").strict(true).schema(schema.build()).build()).build();
    }

    static Map<String, Object> run(String text, String model, Quickstart.Gateway gateway,
                                    List<ResponseInputItem> history) {
        history.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER).content(text).build()));
        var result = new LinkedHashMap<String, Object>();
        var evidence = new ArrayList<Map<String, Object>>();
        var turns = new ArrayList<Map<String, Object>>();
        result.put("provider", "OPENAI");
        result.put("request", Map.of("model", model, "input", text, "tools", List.of(CustomerTool.TOOL_NAME),
                "max_model_requests", CustomerTool.MAX_REQUESTS));
        result.put("status", "NOT_VERIFIED");
        result.put("next_action", "HOLD");
        result.put("answer", "");
        result.put("model_requests", 0);
        result.put("tool_results", evidence);
        result.put("turns", turns);
        result.put("response_schema", CustomerAnswer.SCHEMA);
        boolean finalPhase = false;
        for (int index = 1; index <= CustomerTool.MAX_REQUESTS; index++) {
            var request = ResponseCreateParams.builder().model(model).store(false)
                    .inputOfResponse(List.copyOf(history)).maxOutputTokens(2048)
                    .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);
            if (finalPhase) request.instructions(CustomerAnswer.instructions(evidence)).text(format());
            else request.instructions(CustomerTool.INSTRUCTIONS).addTool(TOOL).parallelToolCalls(false);
            result.put("model_requests", index);
            Quickstart.Turn response;
            try { response = gateway.create(request.build()); }
            catch (RuntimeException error) {
                result.putAll(CustomerAnswer.hold("PROVIDER_ERROR"));
                result.put("message", "OpenAI 응답을 받지 못했습니다. 연결·모델·API 사용 한도를 확인하세요.");
                if (error instanceof OpenAIServiceException service) result.put("http_status", service.statusCode());
                return result;
            }
            var turn = new LinkedHashMap<String, Object>();
            turn.put("request_number", index);
            turn.put("phase", finalPhase ? "STRUCTURED_ANSWER" : "TOOL_SELECTION");
            turn.put("input_items", history.size());
            turn.put("response_status", response.status());
            turn.put("usage_status", response.usage() == null ? "UNAVAILABLE" : "REPORTED");
            turn.put("usage", response.usage());
            turns.add(turn);
            if (response.refused()) return stop(result, "REFUSED");
            if (!"completed".equals(response.status())) return stop(result, "INCOMPLETE");
            var calls = response.output().stream().flatMap(item -> item.functionCall().stream()).toList();
            if (finalPhase) {
                if (!calls.isEmpty()) return stop(result, "INVALID_OUTPUT");
                result.put("structured_response", response.text());
                result.putAll(CustomerAnswer.consume(response.text(), evidence));
                if ("MODEL_RESPONSE".equals(result.get("status"))) appendOutput(history, response);
                return result;
            }
            if (calls.isEmpty()) {
                if (response.text().isBlank()) return stop(result, "INVALID_OUTPUT");
                // The draft is not accepted as a final answer; request the same structured contract.
                finalPhase = true;
                continue;
            }
            if (calls.size() != 1) return stop(result, "INVALID_OUTPUT");
            if (index == CustomerTool.MAX_REQUESTS) return stop(result, "STOPPED");
            var call = calls.get(0);
            Map<String, Object> args = parseArguments(call.arguments());
            var facts = CustomerTool.executeCall(call.name(), args);
            var item = new LinkedHashMap<String, Object>();
            item.put("name", call.name());
            item.put("call_id", call.callId());
            item.put("arguments", args);
            item.put("result", facts);
            evidence.add(item);
            appendOutput(history, response);
            history.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                    .callId(call.callId()).output(OpenAiCustomerAssistant.json(facts)).build()));
            if (facts.containsKey("error") && !"CUSTOMER_NOT_FOUND".equals(facts.get("error")))
                return stop(result, "TOOL_ERROR");
            finalPhase = true;
        }
        return stop(result, "STOPPED");
    }

    static Map<String, Object> parseArguments(String raw) {
        try {
            var node = ARGUMENTS.readTree(raw);
            if (node == null || !node.isObject()) return null;
            return ARGUMENTS.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception error) { return null; }
    }

    private static void appendOutput(List<ResponseInputItem> history, Quickstart.Turn turn) {
        // Preserve every item, including reasoning and the matching function call.
        turn.output().forEach(item -> history.add(Quickstart.JSON.convertValue(item, ResponseInputItem.class)));
    }

    private static Map<String, Object> stop(Map<String, Object> result, String reason) {
        result.putAll(CustomerAnswer.hold(reason));
        return result;
    }

    static Quickstart.Turn decode(Response response) {
        String text = response.output().stream().flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text).reduce("", String::concat);
        boolean refused = response.output().stream().flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream()).anyMatch(content -> content.refusal().isPresent());
        var usage = response.usage().map(value -> new Quickstart.Usage(value.inputTokens(),
                value.inputTokensDetails().cachedTokens(), value.outputTokens(),
                value.outputTokensDetails().reasoningTokens())).orElse(null);
        return new Quickstart.Turn(response.status().map(Object::toString).orElse("unknown"),
                text, response.output(), refused, usage);
    }

    static void chat(String model, Quickstart.Gateway gateway, String mode,
                     BufferedReader input, PrintWriter output) throws IOException {
        var history = new ArrayList<ResponseInputItem>();
        output.println("대화를 시작합니다. 질문을 입력하세요. 종료: /exit");
        while (true) {
            output.print("입력> ");
            output.flush();
            String text = input.readLine();
            if (text == null || "/exit".equals(text.strip())) break;
            if (text.isBlank()) continue;
            var result = run(text.strip(), model, gateway, history);
            result.put("mode", mode);
            output.println(OpenAiCustomerAssistant.json(result));
            if (!"MODEL_RESPONSE".equals(result.get("status"))) {
                output.println("대화를 중단합니다: " + result.get("status"));
                output.flush();
                return;
            }
        }
        output.println("대화를 종료합니다.");
        output.flush();
    }

    static List<String> missingSettings(Map<String, String> settings) {
        var missing = new ArrayList<String>();
        for (String key : List.of("OPENAI_API_KEY", "OPENAI_MODEL"))
            if (settings.getOrDefault(key, "").isBlank()) missing.add(key);
        if (!"1".equals(settings.get("AI_AX_LIVE"))) missing.add("AI_AX_LIVE=1");
        return missing;
    }

    public static void main(String[] args) throws Exception {
        boolean offline = false, chat = false;
        String text = CustomerTool.DEFAULT_TEXT;
        boolean textSpecified = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--offline" -> offline = true;
                case "--chat" -> chat = true;
                case "--tools", "--structured" -> { /* This entry point always uses both. */ }
                case "--text" -> {
                    if (++i == args.length) { invalidArguments(); return; }
                    text = args[i];
                    textSpecified = true;
                }
                default -> { invalidArguments(); return; }
            }
        }
        if (chat && textSpecified) { invalidArguments(); return; }
        if (offline) {
            execute(text, chat, "scripted-offline", OpenAiCustomerOffline.gateway(), "SCRIPTED_OFFLINE");
            return;
        }
        // Only the learner's live IDE run reaches authentication. Never log these settings.
        var settings = Map.of("OPENAI_API_KEY", Objects.toString(System.getenv("OPENAI_API_KEY"), ""),
                "OPENAI_MODEL", Objects.toString(System.getenv("OPENAI_MODEL"), ""),
                "AI_AX_LIVE", Objects.toString(System.getenv("AI_AX_LIVE"), ""));
        var missing = missingSettings(settings);
        if (!missing.isEmpty()) {
            System.out.println(OpenAiCustomerAssistant.json(Map.of("status", "NOT_VERIFIED", "mode", "NOT_STARTED",
                    "missing_settings", missing, "message", "IDE의 비공유 실행 구성에서 OpenAI 환경변수를 설정하세요.")));
            return;
        }
        var client = OpenAIOkHttpClient.builder().apiKey(settings.get("OPENAI_API_KEY"))
                .timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        try {
            execute(text, chat, settings.get("OPENAI_MODEL"),
                    request -> decode(client.responses().create(request)), "LIVE");
        } finally { client.close(); }
    }

    private static void execute(String text, boolean chat, String model, Quickstart.Gateway gateway,
                                String mode) throws IOException {
        if (chat) chat(model, gateway, mode,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8));
        else {
            var result = run(text, model, gateway, new ArrayList<>());
            result.put("mode", mode);
            System.out.println(OpenAiCustomerAssistant.json(result));
        }
    }

    private static void invalidArguments() {
        System.out.println(OpenAiCustomerAssistant.json(Map.of("status", "INVALID_ARGUMENTS", "mode", "NOT_STARTED",
                "message", "--text 또는 --chat을 사용하세요. --offline은 대역 실행이며 --tools --structured도 허용합니다.")));
    }
}
