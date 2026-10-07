package lab.inquiry.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class StatusJson {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private StatusJson() {}

    static String text(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (JsonProcessingException error) {
            throw new IllegalStateException("조회 결과를 JSON으로 변환할 수 없습니다.", error);
        }
    }
}
