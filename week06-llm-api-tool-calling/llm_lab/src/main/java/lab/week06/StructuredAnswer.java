package lab.week06;

import com.openai.core.JsonValue;
import com.openai.models.responses.*;
import java.util.*;

/** 조회 사실 → 스키마 응답 → 사실 검사 → 다음 처리. 모델은 사실의 원장이 아닙니다. */
public final class StructuredAnswer {
    public static ResponseTextConfig format() {
        var properties = new LinkedHashMap<String, Object>();
        for (String field : List.of("customer_id", "plan", "account_status", "answer"))
            properties.put(field, Map.of("type", "string"));
        properties.put("needs_follow_up", Map.of("type", "boolean"));
        var schema = ResponseFormatTextJsonSchemaConfig.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(properties))
            .putAdditionalProperty("required", JsonValue.from(new ArrayList<>(properties.keySet())))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        return ResponseTextConfig.builder().format(ResponseFormatTextJsonSchemaConfig.builder()
            .name("customer_answer").strict(true).schema(schema).build()).build();
    }

    public static Map<String, Object> run(String customerId, Quickstart.Gateway model, String modelName) {
        var facts = customerId.isBlank() ? Map.<String, Object>of("error", "MISSING_ID")
            : CustomerDirectory.lookup(customerId);
        Quickstart.Turn turn;
        try {
            turn = model.create(ResponseCreateParams.builder().model(modelName).store(false)
                .instructions("주어진 고객 사실만 안내하세요. error가 있으면 plan과 account_status는 빈 문자열, "
                    + "needs_follow_up은 true로 하고 ID 확인을 요청하세요. 정상 조회면 false입니다. "
                    + "customer_id는 입력의 값을 유지하세요. answer는 한국어 안내입니다.")
                .input(Quickstart.json(Map.of("customer_id", customerId, "facts", facts)))
                .text(format()).maxOutputTokens(700).build());
        } catch (RuntimeException error) { return stopped("PROVIDER_ERROR"); }
        if (turn.refused()) return stopped("REFUSED");
        if (!turn.status().equals("completed")) return stopped("INCOMPLETE");
        return consume(turn.text(), customerId, facts);
    }

    static Map<String, Object> consume(String text, String id, Map<String, Object> facts) {
        try {
            var data = Quickstart.ARGUMENTS.readTree(text);
            var fields = Set.of("customer_id", "plan", "account_status", "answer", "needs_follow_up");
            if (data == null || !data.isObject() || data.size() != fields.size()) return stopped("INVALID_OUTPUT");
            for (String field : fields) {
                if (!data.has(field) || (field.equals("needs_follow_up") ? !data.get(field).isBoolean()
                    : !data.get(field).isTextual())) return stopped("INVALID_OUTPUT");
            }
            boolean missing = facts.containsKey("error");
            if (!id.equals(data.get("customer_id").asText())
                || !Objects.equals(missing ? "" : facts.get("plan"), data.get("plan").asText())
                || !Objects.equals(missing ? "" : facts.get("status"), data.get("account_status").asText())
                || missing != data.get("needs_follow_up").asBoolean() || data.get("answer").asText().isBlank())
                return stopped("FACT_MISMATCH");
            // 자유 문장의 의미까지 이 대조로 증명하지 않습니다. 원문과 읽어 비교해야 합니다.
            return Map.of("status", missing ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT",
                "data", Quickstart.JSON.convertValue(data, Map.class));
        } catch (Exception error) { return stopped("INVALID_OUTPUT"); }
    }

    static Map<String, Object> stopped(String status) {
        return Map.of("status", status, "next_action", "결과를 확정하지 않고 입력과 모델 응답을 확인합니다.");
    }

    public static void main(String[] args) {
        boolean live = Arrays.asList(args).contains("--live");
        String id = Quickstart.option(args, "--customer", "C-100");
        Quickstart.Gateway gateway = live ? Quickstart.liveClient() : request -> {
            var facts = CustomerDirectory.lookup(id);
            boolean missing = facts.containsKey("error");
            return new Quickstart.Turn("completed", Quickstart.json(Map.of("customer_id", id,
                "plan", missing ? "" : facts.get("plan"), "account_status", missing ? "" : facts.get("status"),
                "needs_follow_up", missing, "answer", missing ? "고객 ID를 확인해 주세요." : "조회한 계정 정보입니다.")), List.of(), false);
        };
        System.out.println(Quickstart.json(Map.of("mode", live ? "LIVE" : "SCRIPTED_OFFLINE",
            "result", run(id, gateway, live ? System.getenv("OPENAI_MODEL") : "offline"))));
    }
}
