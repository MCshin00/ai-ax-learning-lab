package lab.desk;
import com.fasterxml.jackson.databind.*;
public final class Json {
    public static final ObjectMapper MAPPER=new ObjectMapper();
    private Json() {}
    public static String write(Object value) {try{return MAPPER.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("JSON 변환 실패",e);}}
    public static JsonNode tree(Object value) {return MAPPER.valueToTree(value);}
    public static JsonNode read(String text) {try{return MAPPER.readTree(text);}catch(Exception e){throw new IllegalArgumentException("JSON 형식 오류",e);}}
    public static <T>T as(JsonNode value,Class<T> type) {try{return MAPPER.treeToValue(value,type);}catch(Exception e){throw new IllegalArgumentException("응답 형식 오류",e);}}
}
