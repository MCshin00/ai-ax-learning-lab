package lab.week11;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;

/** Java 객체와 HTTP JSON의 항목 이름·타입을 연결합니다. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private Json() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (IOException e) { throw new IllegalArgumentException("결과를 JSON으로 변환하지 못했습니다.", e); }
    }
    public static JsonNode tree(Object value) { return MAPPER.valueToTree(value); }
    public static JsonNode read(String text) {
        try { return MAPPER.readTree(text); }
        catch (IOException e) { throw new IllegalArgumentException("JSON 형식을 확인하세요.", e); }
    }
    public static <T> T convert(JsonNode node, Class<T> type) {
        try { return MAPPER.treeToValue(node, type); }
        catch (IOException e) { throw new IllegalArgumentException("응답 항목과 타입을 확인하세요.", e); }
    }
}
