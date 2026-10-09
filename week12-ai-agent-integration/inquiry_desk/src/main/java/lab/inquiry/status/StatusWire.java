package lab.inquiry.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static lab.inquiry.status.LookupResult.Cause.*;
import static lab.inquiry.status.LookupResult.Outcome.*;

/** MCP와 운영 JSON의 공통 계약. 내부 레코드의 자동 직렬화에는 의존하지 않는다. */
final class StatusWire {
    static final String TOOL_NAME = "get_service_status";
    static final String DESCRIPTION = "서비스 ID로 현재 서비스 상태를 조회한다. 자료에 없는 서비스는 NOT_FOUND로 돌려준다.";
    static final ObjectMapper JSON = new ObjectMapper();

    private StatusWire() {}

    static Tool tool() {
        return Tool.builder().name(TOOL_NAME).description(DESCRIPTION)
                .inputSchema(new JsonSchema("object", Map.of("serviceId", Map.of("type", "string")),
                        List.of("serviceId"), false, null, null))
                .outputSchema(outputSchema()).build();
    }

    static Map<String, Object> outputSchema() {
        try (var input = StatusWire.class.getResourceAsStream("status-output-schema.json")) {
            return JSON.readValue(Objects.requireNonNull(input), new TypeReference<>() {});
        } catch (IOException e) { throw new IllegalStateException("응답 형식 선언을 읽지 못했습니다.", e); }
    }

    static Map<String, Object> fields(LookupResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("outcome", result.outcome().name());
        if (result.serviceId() != null) value.put("serviceId", result.serviceId());
        if (result.outcome() == FOUND) {
            value.put("state", result.state());
            value.put("detail", result.detail());
            value.put("revision", result.revision());
        }
        if (result.isError()) {
            value.put("code", result.code().name());
            if (result.code() == UNKNOWN) value.put("receivedCode", result.receivedCode());
            value.put("message", result.message());
        }
        return value;
    }

    static CallToolResult encode(LookupResult result) {
        var value = fields(result);
        return new CallToolResult(List.of(new TextContent(text(value))), result.isError(), value, null);
    }

    static String text(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON 변환에 실패했습니다.", e); }
    }

    static Map<String, Object> listing(List<Tool> tools) {
        return Map.of("tools", tools.stream().map(tool -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", tool.name());
            value.put("description", tool.description());
            value.put("inputSchema", tool.inputSchema());
            value.put("outputSchema", tool.outputSchema());
            return value;
        }).toList());
    }

    static LookupResult decode(String requestedId, CallToolResult response) {
        if (response == null) return LookupResult.failed(requestedId, INVALID_RESPONSE);
        JsonNode value = JSON.valueToTree(response.structuredContent());
        if (value == null || !value.isObject() || !string(value, "outcome"))
            return LookupResult.failed(requestedId, INVALID_RESPONSE);
        LookupResult.Outcome outcome;
        try { outcome = LookupResult.Outcome.valueOf(value.path("outcome").textValue()); }
        catch (IllegalArgumentException e) { return LookupResult.failed(requestedId, INVALID_RESPONSE); }
        boolean error = outcome == UNAVAILABLE || outcome == INVALID_INPUT;
        if (error != Boolean.TRUE.equals(response.isError())) return LookupResult.failed(requestedId, INVALID_RESPONSE);

        if (outcome != INVALID_INPUT && !string(value, "serviceId")) return LookupResult.failed(requestedId, INVALID_RESPONSE);
        if (outcome == FOUND && (!string(value, "state") || !string(value, "detail")
                || !value.path("revision").isIntegralNumber())) return LookupResult.failed(requestedId, INVALID_RESPONSE);
        if (error && (!string(value, "code") || !string(value, "message"))) return LookupResult.failed(requestedId, INVALID_RESPONSE);
        if (outcome != INVALID_INPUT && !Objects.equals(requestedId, value.path("serviceId").textValue()))
            return LookupResult.failed(requestedId, INVALID_RESPONSE);

        if (outcome == FOUND) return LookupResult.found(requestedId, value.path("state").textValue(),
                value.path("detail").textValue(), value.path("revision").longValue());
        if (outcome == NOT_FOUND) return LookupResult.absent(requestedId);
        String received = value.path("code").textValue();
        LookupResult.Cause cause;
        // 서버가 보내는 원인 값만 해석한다. 클라이언트 전용 값은 서버 계약의 원인 값이 아니다.
        switch (received) {
            case "DATA_UNREADABLE" -> cause = DATA_UNREADABLE;
            case "DATA_INVALID" -> cause = DATA_INVALID;
            case "INVALID_ARGUMENTS" -> cause = INVALID_ARGUMENTS;
            default -> { return LookupResult.error(outcome, requestedId, UNKNOWN, received, UNKNOWN.message()); }
        }
        if ((outcome == INVALID_INPUT) != (cause == INVALID_ARGUMENTS)) return LookupResult.failed(requestedId, INVALID_RESPONSE);
        return LookupResult.error(outcome, outcome == INVALID_INPUT ? null : requestedId,
                cause, null, value.path("message").textValue());
    }

    private static boolean string(JsonNode value, String field) { return value.path(field).isTextual(); }
}
