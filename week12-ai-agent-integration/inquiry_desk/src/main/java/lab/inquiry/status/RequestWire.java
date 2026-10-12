package lab.inquiry.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static lab.inquiry.status.RequestResult.Outcome.*;

public final class RequestWire {
    private RequestWire() {}

    static Map<String, Object> schema(String name) {
        try (var input = RequestWire.class.getResourceAsStream(name + "-schema.json")) {
            return StatusWire.JSON.readValue(input, new TypeReference<>() {});
        } catch (IOException e) { throw new IllegalStateException("요청 도구 형식 선언을 읽지 못했습니다.", e); }
    }

    static Tool tool(String operation) {
        return Tool.builder().name(operation + "_request")
                .description(operation.equals("save") ? "검토한 서비스의 변경 번호를 대조하고 담당자의 요청을 저장한다."
                        : "요청 ID로 저장된 작업 요청을 조회한다.")
                .inputSchema(StatusWire.JSON.convertValue(schema(operation + "-request-input"), JsonSchema.class))
                .outputSchema(schema(operation + "-request-output")).build();
    }

    static boolean valid(String operation, Map<String, Object> arguments) {
        return arguments != null && McpJsonDefaults.getSchemaValidator()
                .validate(schema(operation + "-request-input"), arguments).valid();
    }

    static RequestResult invalid(String operation) {
        return RequestResult.error(INVALID_INPUT, null, "INVALID_ARGUMENTS", operation.equals("save")
                ? "reviewId와 text는 공백이 아닌 문자열이고 services, sources, unprocessed는 선언된 배열이어야 하며 다른 인수는 받지 않습니다."
                : "requestId는 공백이 아닌 문자열 하나여야 하고 다른 인수는 받지 않습니다.");
    }

    public static List<Map<String, Object>> services(List<RequestData.Service> services) {
        return services.stream().map(service -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("serviceId", service.serviceId());
            fields.put("revision", service.revision());
            return fields;
        }).toList();
    }

    public static List<Map<String, Object>> unprocessed(List<RequestData.Unprocessed> unprocessed) {
        return unprocessed.stream().map(item -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            if (item.serviceId() != null) fields.put("serviceId", item.serviceId());
            fields.put("code", item.code());
            return fields;
        }).toList();
    }

    public static Map<String, Object> arguments(RequestData data) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reviewId", data.reviewId());
        fields.put("text", data.text());
        fields.put("services", services(data.services()));
        fields.put("sources", data.sources());
        fields.put("unprocessed", unprocessed(data.unprocessed()));
        return fields;
    }

    public static Map<String, Object> fields(RequestResult result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("outcome", result.outcome().name());
        if (result.requestId() != null) fields.put("requestId", result.requestId());
        if (result.request() != null) fields.putAll(arguments(result.request()));
        if (result.serviceId() != null) fields.put("serviceId", result.serviceId());
        if (result.isError()) {
            fields.put("code", result.code());
            fields.put("message", result.message());
        }
        return fields;
    }

    static CallToolResult encode(RequestResult result) {
        var fields = fields(result);
        return new CallToolResult(List.of(new TextContent(StatusWire.text(fields))), result.isError(), fields, null);
    }

    static RequestData data(Object input) {
        JsonNode value = StatusWire.JSON.valueToTree(input);
        var services = new ArrayList<RequestData.Service>();
        for (var item : array(value, "services")) {
            if (!item.path("revision").isIntegralNumber()) throw new IllegalArgumentException();
            services.add(new RequestData.Service(string(item, "serviceId"), item.get("revision").longValue()));
        }
        var sources = new ArrayList<String>();
        for (var item : array(value, "sources")) {
            if (!item.isTextual()) throw new IllegalArgumentException();
            sources.add(item.textValue());
        }
        var unprocessed = new ArrayList<RequestData.Unprocessed>();
        for (var item : array(value, "unprocessed")) {
            unprocessed.add(new RequestData.Unprocessed(item.has("serviceId") ? string(item, "serviceId") : null,
                    string(item, "code")));
        }
        return new RequestData(string(value, "reviewId"), string(value, "text"), services, sources, unprocessed);
    }

    static RequestResult decode(CallToolResult response) {
        try {
            JsonNode value = StatusWire.JSON.valueToTree(response.structuredContent());
            var outcome = RequestResult.Outcome.valueOf(string(value, "outcome"));
            return switch (outcome) {
                case SAVED -> RequestResult.saved(string(value, "requestId"));
                case FOUND -> RequestResult.found(string(value, "requestId"), data(value));
                case NOT_FOUND -> RequestResult.absent(string(value, "requestId"));
                case CONFLICT, STATUS_CHANGED, INVALID_INPUT, UNAVAILABLE -> RequestResult.error(outcome,
                        outcome == STATUS_CHANGED ? string(value, "serviceId") : null,
                        string(value, "code"), string(value, "message"));
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException | NullPointerException e) {
            var cause = LookupResult.Cause.INVALID_RESPONSE;
            return RequestResult.unavailable(cause.name(), cause.message());
        }
    }

    private static String string(JsonNode value, String field) {
        if (!value.path(field).isTextual()) throw new IllegalArgumentException();
        return value.get(field).textValue();
    }

    private static JsonNode array(JsonNode value, String field) {
        if (!value.path(field).isArray()) throw new IllegalArgumentException();
        return value.get(field);
    }
}
