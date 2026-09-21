package lab.week06;

import java.util.*;

/** Provider-independent customer lookup contract and projection. */
final class CustomerTool {
    static final String TOOL_NAME = "get_customer_context";
    static final int MAX_REQUESTS = 3;
    static final Set<String> ALLOWED_FIELDS = Set.of("plan", "status");
    static final String DEFAULT_TEXT = "C-100 고객의 요금제를 알려주세요.";
    static final String INSTRUCTIONS = "한국어로 간결하게 답하세요. 고객 요금제와 상태는 반드시 "
            + "get_customer_context의 실제 조회 결과에 근거하세요. 고객 번호가 없으면 질문하고 "
            + "번호를 추측하지 마세요. CUSTOMER_NOT_FOUND이면 고객 번호 확인을 안내하세요. "
            + "질문한 항목만 fields에 지정하세요. 요금제는 plan, 계정 상태는 status이며 "
            + "둘 다 물으면 두 항목을 함께 지정하세요. 반환되지 않은 고객 정보는 추측하지 마세요. "
            + "한 응답에는 함수 호출을 최대 하나만 요청하고, 조회 결과가 있으면 답하세요.";
    static Map<String, Object> executeCall(String name, Map<String, Object> args) {
        if (!TOOL_NAME.equals(name)) return Map.of("error", "UNKNOWN_TOOL");
        if (args == null || !args.keySet().equals(Set.of("customer_id", "fields"))
                || !(args.get("customer_id") instanceof String id) || id.isBlank()
                || !(args.get("fields") instanceof List<?> fields) || fields.isEmpty())
            return Map.of("error", "INVALID_ARGUMENTS");
        var selected = new LinkedHashSet<String>();
        for (Object field : fields) {
            if (!(field instanceof String requested) || !ALLOWED_FIELDS.contains(requested))
                return Map.of("error", "INVALID_ARGUMENTS");
            selected.add(requested);
        }
        var customer = CustomerDirectory.lookup(id);
        if (customer.containsKey("error")) return customer;
        var result = new LinkedHashMap<String, Object>();
        result.put("customer_id", customer.get("customer_id"));
        for (String field : selected) result.put(field, customer.get(field));
        return result;
    }

}
