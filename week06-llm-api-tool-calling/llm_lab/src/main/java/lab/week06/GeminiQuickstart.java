package lab.week06;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Gemini entry point: plain summary by default, lookup with --tools, conversation with --chat. */
public final class GeminiQuickstart {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String DEFAULT_TEXT =
            "메모를 한 문장으로 요약해줘: 로그인 수정 완료, 배포 확인 필요.";
    static final String INSTRUCTIONS =
            "한국어로 간결하게 요약하세요. 입력의 사실과 완료 여부를 보존하고, "
            + "확인 필요나 미완료인 일을 완료된 사실로 바꾸지 마세요.";
    static final int MAX_OUTPUT_TOKENS = 2048;

    @FunctionalInterface
    interface Gateway {
        GenerateContentResponse generate(String model, String text, GenerateContentConfig config);
    }

    static GenerateContentConfig requestConfig() {
        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(INSTRUCTIONS)))
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                // Select a Gemini model supporting the LOW thinking level.
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel("LOW").build())
                .build();
    }

    static Map<String, Object> run(String text, String model, Gateway gateway) {
        var result = baseResult();
        result.put("request", Map.of(
                "model", model, "input", text, "instructions", INSTRUCTIONS,
                "max_output_tokens", MAX_OUTPUT_TOKENS, "thinking_level", "LOW",
                "tools", List.of()));
        result.put("model_requests", 1);
        GenerateContentResponse response;
        try {
            response = gateway.generate(model, text, requestConfig());
        } catch (ApiException error) {
            result.put("status", "PROVIDER_ERROR");
            result.put("http_status", error.code());
            result.put("message", switch (error.code()) {
                case 400 -> "모델과 요청 설정을 확인하세요.";
                case 401, 403 -> "API 키와 프로젝트의 API 사용 권한을 확인하세요.";
                case 404 -> "GEMINI_MODEL의 모델 ID와 사용 가능 여부를 확인하세요.";
                case 429 -> "AI Studio에서 이 모델의 할당량과 재설정 시점을 확인하세요.";
                default -> "API 응답을 받지 못했습니다. 연결과 서비스 상태를 확인하세요.";
            });
            return result;
        } catch (RuntimeException error) {
            result.put("status", "PROVIDER_ERROR");
            result.put("message", "API 응답을 받지 못했습니다. 연결 상태를 확인하세요.");
            return result;
        }

        try {
            JsonNode body = JSON.readTree(response.toJson());
            JsonNode candidate = body.path("candidates").path(0);
            String finishReason = candidate.path("finishReason").asText("UNAVAILABLE");
            String blockReason = body.path("promptFeedback").path("blockReason")
                    .asText("BLOCK_REASON_UNSPECIFIED");
            String answer = Objects.toString(response.text(), "");
            result.put("finish_reason", finishReason);
            result.put("answer", answer);
            if (body.hasNonNull("usageMetadata")) {
                // Keep Gemini's reported fields; do not invent missing counts or OpenAI aliases.
                result.put("usage_status", "REPORTED");
                result.put("usage", body.get("usageMetadata"));
            }
            if (!blockReason.equals("BLOCK_REASON_UNSPECIFIED")) {
                result.put("status", "REFUSED");
                result.put("block_reason", blockReason);
            } else if (Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT",
                    "SPII", "IMAGE_SAFETY").contains(finishReason)) {
                result.put("status", "REFUSED");
            } else if (!"STOP".equals(finishReason)) {
                result.put("status", "INCOMPLETE");
            } else if (answer.isBlank() || !candidate.findValues("functionCall").isEmpty()) {
                result.put("status", "INVALID_OUTPUT");
            } else {
                result.put("status", "MODEL_RESPONSE");
            }
        } catch (Exception error) {
            result.put("status", "INVALID_OUTPUT");
            result.put("message", "응답 형식을 해석하지 못했습니다.");
        }
        return result;
    }

    private static Map<String, Object> baseResult() {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "NOT_VERIFIED");
        result.put("answer", "");
        result.put("model_requests", 0);
        result.put("tool_results", List.of());
        result.put("usage_status", "UNAVAILABLE");
        result.put("usage", null);
        return result;
    }

    static List<String> missingSettings(Map<String, String> environment) {
        var missing = new ArrayList<String>();
        for (String name : List.of("GEMINI_API_KEY", "GEMINI_MODEL")) {
            if (environment.getOrDefault(name, "").isBlank()) missing.add(name);
        }
        if (!"1".equals(environment.get("AI_AX_LIVE"))) missing.add("AI_AX_LIVE=1");
        return missing;
    }

    public static void main(String[] args) throws Exception {
        String text = null;
        boolean offline = false;
        boolean tools = false;
        boolean plain = false;
        boolean chat = false;
        boolean caseSpecified = false;
        String scenario = "normal";
        for (int i = 0; i < args.length; i++) {
            if ("--offline".equals(args[i])) offline = true;
            else if ("--plain".equals(args[i])) plain = true;
            else if ("--tools".equals(args[i])) tools = true;
            else if ("--chat".equals(args[i])) chat = true;
            else if ("--case".equals(args[i]) && i + 1 < args.length) {
                scenario = args[++i];
                caseSpecified = true;
            }
            else if ("--text".equals(args[i]) && i + 1 < args.length) text = args[++i];
            else {
                var error = baseResult();
                error.put("status", "INVALID_ARGUMENTS");
                error.put("message", "프로그램 인자는 --plain, --tools, --chat, --text, --offline, --case normal|status|both|unknown|repeat를 사용하세요.");
                print(error);
                return;
            }
        }

        if ((plain && tools) || !Set.of("normal", "status", "both", "unknown", "repeat").contains(scenario)
                || (caseSpecified && !(offline && tools))
                || (chat && (plain || tools || text != null || caseSpecified))) {
            var error = baseResult();
            error.put("status", "INVALID_ARGUMENTS");
            error.put("message", "--chat은 단독 또는 --offline과 사용하세요. --plain과 --tools는 함께 쓰지 않습니다. --case는 --tools --offline에서 사용하세요.");
            print(error);
            return;
        }
        if (text == null) text = tools ? switch (scenario) {
            case "status" -> "C-100 고객의 계정 상태를 알려주세요.";
            case "both" -> "C-100 고객의 요금제와 계정 상태를 알려주세요.";
            case "unknown" -> "C-404 고객의 요금제를 알려주세요.";
            default -> GeminiToolLoop.DEFAULT_TEXT;
        } : DEFAULT_TEXT;
        Map<String, Object> result;
        if (offline) {
            if (chat) {
                chat("scripted-offline", GeminiChat.offline(), "SCRIPTED_OFFLINE");
                return;
            }
            if (tools) result = GeminiToolLoop.run(text, "scripted-offline", GeminiToolLoop.offline(scenario));
            else
            result = run(text, "scripted-offline", (model, input, config) ->
                    GenerateContentResponse.fromJson("""
                        {"candidates":[{"finishReason":"STOP","content":{"role":"model",
                        "parts":[{"text":"이 문장은 고정된 오프라인 예시입니다."}]}}]}
                        """));
            result.put("mode", "SCRIPTED_OFFLINE");
        } else {
            var environment = System.getenv();
            var missing = missingSettings(environment);
            if (!missing.isEmpty()) {
                result = baseResult();
                result.put("mode", "NOT_STARTED");
                result.put("message", "GeminiQuickstart 실행 구성의 환경변수를 설정하세요.");
                result.put("missing_settings", missing);
                print(result);
                return;
            }
            // Explicit key selects the Developer API and avoids GOOGLE_API_KEY precedence.
            try (Client client = Client.builder()
                    .apiKey(environment.get("GEMINI_API_KEY"))
                    .httpOptions(HttpOptions.builder().timeout(30_000)
                            .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                    .build()) {
                if (chat) {
                    chat(environment.get("GEMINI_MODEL"),
                            (model, history, config) -> client.models.generateContent(model, history, config), "LIVE");
                    return;
                }
                if (tools) result = GeminiToolLoop.run(text, environment.get("GEMINI_MODEL"),
                        (model, history, config) -> client.models.generateContent(model, history, config));
                else result = run(text, environment.get("GEMINI_MODEL"),
                        (model, input, config) -> client.models.generateContent(model, input, config));
            }
            result.put("mode", "LIVE");
        }
        print(result);
    }

    private static void chat(String model, GeminiToolLoop.Gateway gateway, String mode) throws IOException {
        GeminiChat.run(model, gateway, mode,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8));
    }

    private static void print(Map<String, Object> result) throws Exception {
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
