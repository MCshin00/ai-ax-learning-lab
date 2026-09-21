package lab.week06;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;

/** Final output contract and application-owned acceptance of customer facts. */
final class CustomerAnswer {
    static final Map<String, Object> SCHEMA;
    static {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("customer_id", Map.of("type", "string"));
        properties.put("plan", Map.of("type", List.of("string", "null")));
        properties.put("account_status", Map.of("type", List.of("string", "null")));
        properties.put("needs_follow_up", Map.of("type", "boolean"));
        properties.put("answer", Map.of("type", "string"));
        SCHEMA = Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
    private static final JsonMapper READER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    static String instructions(List<Map<String, Object>> results) {
        String evidence;
        try { evidence = READER.writeValueAsString(results); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid lookup evidence"); }
        return "최종 응답을 지정된 JSON 형식으로 반환하고 answer는 한국어로 쓰세요. "
                    + "고객 사실은 아래 이번 조회 근거만 사용하세요. 이전 대화의 고객 값을 복사하지 마세요. "
                    + "성공이면 customer_id와 조회된 plan/status를 그대로 옮기고 needs_follow_up=false로 하세요. "
                    + "status는 account_status에 옮기세요. 조회되지 않은 항목은 null입니다. "
                    + "CUSTOMER_NOT_FOUND이면 인자의 customer_id를 유지하고 두 값은 null, "
                    + "needs_follow_up=true로 하며 고객 번호 확인을 요청하세요. "
                    + "이번 조회 근거가 비어 있으면 customer_id는 빈 문자열, 두 값은 null, "
                    + "needs_follow_up=true로 하고 고객 번호를 요청하세요. 번호나 사실을 추측하지 마세요. "
                    + "도구 호출 대신 최종 JSON만 반환하세요. 이번 조회 근거: " + evidence;
    }

    static Map<String, Object> consume(String raw, List<Map<String, Object>> results) {
        try {
            var data = READER.readTree(raw);
            var keys = Set.of("customer_id", "plan", "account_status", "needs_follow_up", "answer");
            if (data == null || !data.isObject() || data.size() != keys.size()) return hold("INVALID_OUTPUT");
            for (String key : keys) if (!data.has(key)) return hold("INVALID_OUTPUT");
            if (!data.get("customer_id").isTextual() || !data.get("answer").isTextual()
                    || data.get("answer").asText().isBlank() || !data.get("needs_follow_up").isBoolean())
                return hold("INVALID_OUTPUT");
            for (String key : List.of("plan", "account_status"))
                if (!data.get(key).isNull() && !data.get(key).isTextual()) return hold("INVALID_OUTPUT");
            String id = "";
            Map<?, ?> facts = Map.of();
            boolean followup = true;
            if (!results.isEmpty()) {
                var latest = results.get(results.size() - 1);
                facts = (Map<?, ?>) latest.get("result");
                if (facts.containsKey("error") && !"CUSTOMER_NOT_FOUND".equals(facts.get("error")))
                    return hold("TOOL_ERROR");
                id = (String) ((Map<?, ?>) latest.get("arguments")).get("customer_id");
                followup = facts.containsKey("error");
            }
            if (!id.equals(data.get("customer_id").asText())
                    || followup != data.get("needs_follow_up").asBoolean()
                    || !Objects.equals(facts.get("plan"), data.get("plan").isNull() ? null : data.get("plan").asText())
                    || !Objects.equals(facts.get("status"), data.get("account_status").isNull() ? null : data.get("account_status").asText()))
                return hold("FACT_MISMATCH");
            var accepted = new LinkedHashMap<String, Object>();
            accepted.put("status", "MODEL_RESPONSE");
            accepted.put("next_action", followup ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT");
            accepted.put("data", READER.convertValue(data, Map.class));
            accepted.put("answer", data.get("answer").asText());
            // Display customer values from validated fields; prose still requires reading against evidence.
            accepted.put("display", followup ? Map.of("question", data.get("answer").asText()) : facts);
            return accepted;
        } catch (Exception error) { return hold("INVALID_OUTPUT"); }
    }

    static Map<String, Object> hold(String reason) {
        return Map.of("status", reason, "next_action", "HOLD", "answer", "",
                "message", "결과를 확정하지 않고 입력과 응답을 확인하세요.");
    }
}
