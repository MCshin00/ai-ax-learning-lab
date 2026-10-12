package lab.inquiry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lab.inquiry.status.RequestResult;
import lab.inquiry.status.RequestWire;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

final class HttpWire {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    static JsonNode input(InputStream stream, String first, String second) throws IOException {
        JsonNode value;
        try { value = JSON.readTree(stream); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return null; }
        if (value == null || !value.isObject() || !value.path(first).isTextual() || !value.path(second).isTextual()
                || value.get(first).textValue().isBlank() || value.get(second).textValue().isBlank()) return null;
        return value;
    }

    static Map<String, Object> invalid(String first, String second) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("outcome", "FAILED");
        fields.put("code", "INVALID_INPUT");
        fields.put("message", "본문은 JSON 객체여야 하고 " + first + "와 " + second + "는 공백이 아닌 문자열이어야 합니다.");
        return fields;
    }

    static Map<String, Object> fields(ReviewSession.Result result) {
        var fields = InquiryWire.fields(result.inquiry());
        var review = result.review();
        if (review != null) {
            var value = new LinkedHashMap<String, Object>();
            value.put("reviewId", review.reviewId());
            value.put("services", RequestWire.services(review.services()));
            value.put("unprocessed", RequestWire.unprocessed(review.unprocessed()));
            fields.put("review", value);
        }
        return fields;
    }

    static int status(RequestResult result) {
        return switch (result.outcome()) {
            case SAVED -> 200;
            case STALE_REVIEW, CONFLICT, STATUS_CHANGED -> 409;
            default -> 503;
        };
    }
}
