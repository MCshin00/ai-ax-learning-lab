package lab.week11;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.*;

/** 준비된 모델 응답으로 AI Services의 실제 도구 실행과 출력 변환을 확인합니다. */
public final class ScriptedModels {
    private ScriptedModels() {}
    public static ChatModel model(String operation, JsonNode payload) {
        var responses = new ArrayList<AiMessage>();
        if (operation.equals("intake")) {
            var fixtures = new LinkedHashMap<String, Consultation.Intake>();
            fixtures.put("A-102 상태가 궁금해요.", intake("status", "A-102"));
            fixtures.put("A-103 상태가 궁금해요.", intake("status", "A-103"));
            fixtures.put("A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.", intake("cancel", "A-102"));
            fixtures.put("반품하고 싶어요", intake("return"));
            fixtures.put("A-104예요", intake("return", "A-104"));
            fixtures.put("받은 지 이틀이고 사용하지 않았어요", new Consultation.Intake("return", List.of("A-104"),
                Map.of("A-104", 2), Map.of("A-104", false)));
            fixtures.put("A-103으로 정정할게요", intake("cancel", "A-103"));
            fixtures.put("A-999, A-102, A-103의 상태를 각각 알려 주세요.", intake("status", "A-999", "A-102", "A-103"));
            var value = fixtures.get(payload.path("request").asText());
            if (value == null) throw new IllegalArgumentException("고정 응답 모드의 제공 입력을 사용하세요.");
            responses.add(AiMessage.from(modelJson(value)));
        } else {
            String intent = payload.path("intake").path("intent").asText();
            if (!intent.equals("status") && payload.path("evidence").isEmpty())
                responses.add(AiMessage.from(ToolExecutionRequest.builder().id("policy-1").name("search_policy")
                    .arguments(Json.write(Map.of("query", intent.equals("cancel") ? "취소 출고" : "반품 수령"))).build()));
            var items = new ArrayList<Consultation.DraftItem>();
            for (var row : payload.path("orders")) {
                var facts = row.path("facts");
                var sources = intent.equals("status") || facts.isNull() ? List.<String>of()
                    : List.of(intent.equals("cancel") && !facts.path("shipped").asBoolean() ? "POL-CANCEL" : "POL-RETURN");
                String answer = facts.path("status").asText("주문을 확인하지 못했습니다.");
                if (!sources.isEmpty()) answer += ". 정책 조건을 확인하고 주문 상세에서 신청 경로를 확인하세요.";
                items.add(new Consultation.DraftItem(row.path("order_id").asText(), answer, sources));
            }
            responses.add(AiMessage.from(modelJson(new Consultation.Draft(items))));
        }
        return sequence(responses);
    }
    public static Consultation.Intake intake(String intent, String... ids) {
        return new Consultation.Intake(intent, List.of(ids), Map.of(), Map.of());
    }
    public static ChatModel sequence(List<AiMessage> responses) {
        return new ChatModel() {
            int index;
            @Override public ChatResponse doChat(ChatRequest request) {
                if (index >= responses.size()) throw new IllegalStateException("준비된 응답을 모두 사용했습니다.");
                return ChatResponse.builder().aiMessage(responses.get(index++)).build();
            }
        };
    }
    // AI Services가 정의한 Java 출력 필드는 camelCase, 앱의 HTTP 출력은 Json의 snake_case입니다.
    public static String modelJson(Object value) {
        try { return new ObjectMapper().writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("대역 응답 형식을 확인하세요.", e); }
    }
    public static EmbeddingModel embeddings() {
        return new EmbeddingModel() {
            @Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return Response.from(segments.stream().map(s -> {
                    String text = s.text();
                    // 주제별 축은 검색 연결 검사에 쓰는 고정 벡터입니다.
                    float[] values = {text.contains("취소") ? 1 : 0, text.contains("반품") ? 1 : 0,
                        text.contains("배송") ? 1 : 0, 0.01f};
                    return Embedding.from(values);
                }).toList());
            }
        };
    }
}
