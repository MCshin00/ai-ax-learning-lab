package lab.week07;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.time.Duration;
import java.util.*;

public final class Quickstart {
    static final ObjectMapper JSON = new ObjectMapper();
    static final ObjectMapper ARGUMENTS = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static final Map<String, Map<String, Object>> CUSTOMERS = new LinkedHashMap<>(
            Map.of("C-100", Map.of("plan", "basic", "status", "active")));
    public static final String INSTRUCTIONS = "고객 ID가 없으면 물어보세요. 고객 정보는 get_customer_context로 조회하세요. "
            + "오류나 없는 고객을 성공으로 설명하지 마세요. 조회만 가능하며 변경할 수 없습니다.";
    public interface Assistant { Result<String> answer(String text); }
    static final class InvalidToolArguments extends RuntimeException {}

    static void validateArguments(ToolExecutionRequest request) {
        if (!request.name().equals("get_customer_context")) return;
        try {
            var data = ARGUMENTS.readTree(request.arguments());
            if (data == null || !data.isObject() || !data.has("customer_id") || !data.get("customer_id").isTextual())
                throw new InvalidToolArguments();
        } catch (java.io.IOException | IllegalArgumentException invalid) { throw new InvalidToolArguments(); }
    }

    public static final class CustomerTools {
        @Tool(name = "get_customer_context", value = "고객 ID로 요금제와 계정 상태를 조회합니다.")
        public String getCustomerContext(String customer_id) {
            if (customer_id == null || customer_id.isBlank()) return json(Map.of("error", "INVALID_CUSTOMER_ID"));
            var data = CUSTOMERS.get(customer_id);
            if (data == null) return json(Map.of("error", "CUSTOMER_NOT_FOUND"));
            var result = new LinkedHashMap<String, Object>(data);
            result.put("customer_id", customer_id);
            return json(result);
        }
    }

    /** Only the model is scripted; AiServices selects and executes the registered tool. */
    public static class OfflineModel implements ChatModel {
        public final List<ChatRequest> requests = new ArrayList<>();
        private final String scenario;
        public OfflineModel(String scenario) { this.scenario = scenario; }
        @Override public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            var messages = request.messages();
            var last = messages.get(messages.size() - 1);
            AiMessage answer;
            if (last instanceof ToolExecutionResultMessage result)
                answer = AiMessage.from("도구가 반환한 결과: " + result.text());
            else if (scenario.equals("missing")) answer = AiMessage.from("고객 ID를 알려주세요.");
            else answer = AiMessage.from(ToolExecutionRequest.builder().id("offline-1").name("get_customer_context")
                        .arguments(json(Map.of("customer_id", scenario.equals("unknown") ? "C-404" : "C-100"))).build());
            return ChatResponse.builder().aiMessage(answer)
                    .finishReason(answer.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP).build();
        }
    }

    public static Map<String, Object> run(String text, ChatModel model) {
        var toolResults = new ArrayList<String>();
        var assistant = AiServices.builder(Assistant.class).chatModel(model).systemMessageProvider(id -> INSTRUCTIONS)
                .tools(new CustomerTools()).maxToolCallingRoundTrips(2)
                .beforeToolExecution(execution -> validateArguments(execution.request()))
                .afterToolExecution(execution -> toolResults.add(execution.result()))
                .hallucinatedToolNameStrategy(request -> {
                    String error = json(Map.of("error", "UNKNOWN_TOOL"));
                    toolResults.add(error);
                    return ToolExecutionResultMessage.from(request, error);
                })
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(json(Map.of("error", "INVALID_ARGUMENTS"))))
                .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text(json(Map.of("error", "TOOL_EXECUTION_FAILED"))))
                .build();
        Result<String> result;
        try { result = assistant.answer(text); }
        catch (InvalidToolArguments invalid) {
            toolResults.add(json(Map.of("error", "INVALID_ARGUMENTS")));
            return new LinkedHashMap<>(Map.of("status", "TOOL_ERROR", "answer", "INVALID_ARGUMENTS", "tool_results", toolResults));
        }
        catch (ContentFilteredException refused) {
            return new LinkedHashMap<>(Map.of("status", "REFUSED", "answer", "모델이 요청에 대한 답변을 거부했습니다.", "tool_results", toolResults));
        }
        catch (RuntimeException error) {
            if (Objects.toString(error.getMessage(), "").contains("maxToolCallingRoundTrips"))
                return new LinkedHashMap<>(Map.of("status", "STOPPED", "answer", "도구 반복 상한에 도달했습니다.", "tool_results", toolResults));
            throw error;
        }
        boolean failed = toolResults.stream().anyMatch(Quickstart::isToolError);
        // The framework has already returned the tool result to the model at this point.
        String status = failed ? "TOOL_ERROR" : result.finishReason() == FinishReason.LENGTH ? "INCOMPLETE"
                : result.finishReason() == FinishReason.CONTENT_FILTER ? "REFUSED"
                : result.finishReason() == FinishReason.STOP ? "MODEL_RESPONSE" : "INCOMPLETE";
        return new LinkedHashMap<>(Map.of("status", status,
                "answer", failed ? "조회에 실패했습니다. 도구 결과를 확인하세요." : Objects.toString(result.content(), ""),
                "tool_results", toolResults));
    }

    static boolean isToolError(String output) {
        try { return JSON.readTree(output).has("error"); }
        catch (Exception e) { return true; }
    }
    public static ChatModel liveModel() {
        if (!"1".equals(System.getenv("AI_AX_LIVE")) || System.getenv("OPENAI_API_KEY") == null
                || System.getenv("OPENAI_MODEL") == null)
            throw new IllegalStateException("Set AI_AX_LIVE=1, OPENAI_API_KEY and OPENAI_MODEL before a billed call.");
        return OpenAiChatModel.builder().apiKey(System.getenv("OPENAI_API_KEY")).modelName(System.getenv("OPENAI_MODEL"))
                .timeout(Duration.ofSeconds(20)).maxRetries(0).maxCompletionTokens(700).parallelToolCalls(false)
                .logRequests(false).logResponses(false).build();
    }
    public static String json(Object value) {
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot serialize result", e); }
    }
    static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length; i++) if (args[i].equals(name)) {
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing value: " + name);
            return args[i + 1];
        }
        return fallback;
    }
    public static void main(String[] args) {
        boolean offline = Arrays.asList(args).contains("--offline");
        String scenario = option(args, "--case", "normal");
        if (!Set.of("normal", "missing", "unknown").contains(scenario)) throw new IllegalArgumentException("Unknown case");
        var result = run(option(args, "--text", "C-100 고객의 요금제를 알려주세요."),
                offline ? new OfflineModel(scenario) : liveModel());
        result.put("mode", offline ? "SCRIPTED_OFFLINE" : "LIVE");
        System.out.println(json(result));
    }
}
