package lab.week06;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.responses.*;
import java.util.*;

/** 조회 사실 → 스키마 응답 → 사실 검사 → 다음 처리. 모델은 사실의 원장이 아닙니다. */
public final class StructuredAnswer {
    // 업무 출력의 null은 미조회 값을 뜻합니다. SDK 전송용 매퍼와 분리해 보존합니다.
    private static final ObjectMapper OUTPUT_JSON = new ObjectMapper();

    static String json(Object value) {
        try { return OUTPUT_JSON.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("업무 결과를 JSON으로 변환하지 못했습니다.", error); }
    }

    public static ResponseTextConfig format() {
        var properties = new LinkedHashMap<String, Object>();
        for (String field : List.of("customer_id", "answer"))
            properties.put(field, Map.of("type", "string"));
        for (String field : List.of("plan", "account_status"))
            properties.put(field, Map.of("type", List.of("string", "null")));
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
        return run(customerId, facts, model, modelName);
    }

    /** 기존 앱의 이번 조회 결과를 그대로 사용합니다. 전체 고객을 다시 조회하지 않습니다. */
    public static Map<String, Object> run(String customerId, Map<String, Object> facts,
                                         Quickstart.Gateway model, String modelName) {
        if (facts.containsKey("customer_id") && !Objects.equals(customerId, facts.get("customer_id")))
            return stopped("FACT_MISMATCH");
        if (facts.containsKey("error") && !needsId(facts)) return stopped("TOOL_ERROR");
        Quickstart.Turn turn;
        try {
            turn = model.create(ResponseCreateParams.builder().model(modelName).store(false)
                .instructions("주어진 이번 조회 사실만 안내하세요. 조회하지 않은 plan 또는 account_status는 null입니다. "
                    + "사실의 status는 account_status에 옮기세요. error가 있으면 두 값은 null, "
                    + "needs_follow_up은 true로 하고 ID 확인을 요청하세요. 정상 조회면 false입니다. "
                    + "customer_id는 입력의 값을 유지하세요. answer는 한국어 안내입니다.")
                .input(json(Map.of("customer_id", customerId, "facts", facts)))
                .text(format()).maxOutputTokens(700).build());
        } catch (RuntimeException error) { return stopped("PROVIDER_ERROR"); }
        if (turn.refused()) return stopped("REFUSED");
        if (!turn.status().equals("completed")) return stopped("INCOMPLETE");
        return consume(turn.text(), customerId, facts);
    }

    static Map<String, Object> consume(String text, String id, Map<String, Object> facts) {
        if (facts.containsKey("customer_id") && !Objects.equals(id, facts.get("customer_id")))
            return stopped("FACT_MISMATCH");
        try {
            var data = Quickstart.ARGUMENTS.readTree(text);
            var fields = Set.of("customer_id", "plan", "account_status", "answer", "needs_follow_up");
            if (data == null || !data.isObject() || data.size() != fields.size()) return stopped("INVALID_OUTPUT");
            for (String field : fields) if (!data.has(field)) return stopped("INVALID_OUTPUT");
            if (!data.get("customer_id").isTextual() || !data.get("answer").isTextual()
                || !data.get("needs_follow_up").isBoolean()) return stopped("INVALID_OUTPUT");
            for (String field : List.of("plan", "account_status"))
                if (!data.get(field).isTextual() && !data.get(field).isNull()) return stopped("INVALID_OUTPUT");
            boolean missing = needsId(facts);
            if (facts.containsKey("error") && !missing) return stopped("TOOL_ERROR");
            if (!id.equals(data.get("customer_id").asText())
                || !Objects.equals(missing ? null : facts.get("plan"), data.get("plan").isNull() ? null : data.get("plan").asText())
                || !Objects.equals(missing ? null : facts.get("status"), data.get("account_status").isNull() ? null : data.get("account_status").asText())
                || missing != data.get("needs_follow_up").asBoolean() || data.get("answer").asText().isBlank())
                return stopped("FACT_MISMATCH");
            // 자유 문장의 의미까지 이 대조로 증명하지 않습니다. 원문과 읽어 비교해야 합니다.
            return Map.of("status", missing ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT",
                "next_action", missing ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT",
                "data", Quickstart.JSON.convertValue(data, Map.class));
        } catch (Exception error) { return stopped("INVALID_OUTPUT"); }
    }

    static Map<String, Object> stopped(String status) {
        return Map.of("status", status, "next_action", "HOLD",
            "message", "결과를 확정하지 않고 입력과 모델 응답을 확인합니다.");
    }

    static boolean needsId(Map<String, Object> facts) {
        return Set.of("MISSING_ID", "CUSTOMER_NOT_FOUND").contains(facts.getOrDefault("error", ""));
    }

    public static void main(String[] args) {
        boolean live = Arrays.asList(args).contains("--live");
        String id = Quickstart.option(args, "--customer", "C-100");
        Quickstart.Gateway gateway = live ? Quickstart.liveClient() : request -> {
            var facts = id.isBlank() ? Map.<String, Object>of("error", "MISSING_ID") : CustomerDirectory.lookup(id);
            boolean missing = facts.containsKey("error");
            var data = new LinkedHashMap<String, Object>();
            data.put("customer_id", id);
            data.put("plan", missing ? null : facts.get("plan"));
            data.put("account_status", missing ? null : facts.get("status"));
            data.put("needs_follow_up", missing);
            data.put("answer", missing ? "고객 ID를 확인해 주세요." : "조회한 계정 정보입니다.");
            return new Quickstart.Turn("completed", json(data), List.of(), false);
        };
        System.out.println(json(Map.of("mode", live ? "LIVE" : "SCRIPTED_OFFLINE",
            "result", run(id, gateway, live ? System.getenv("OPENAI_MODEL") : "offline"))));
    }
}
