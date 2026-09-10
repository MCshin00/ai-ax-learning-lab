package lab.week05;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;

/** 행별 입력 오류도 처리 결과로 남긴다. 파일·전체 형식 오류와 구분한다. */
public final class BatchRefund {
    private BatchRefund() {}
    public static JsonArray process(JsonElement input) {
        if (!input.isJsonArray()) throw new IllegalArgumentException("입력은 요청 배열이어야 합니다.");
        var ids = new HashSet<String>();
        for (JsonElement value : input.getAsJsonArray()) {
            if (!value.isJsonObject() || !value.getAsJsonObject().has("id")) {
                throw new IllegalArgumentException("각 요청에는 고유한 문자열 id가 필요합니다.");
            }
            var id = value.getAsJsonObject().get("id");
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString() || !ids.add(id.getAsString())) {
                throw new IllegalArgumentException("각 요청에는 고유한 문자열 id가 필요합니다.");
            }
        }
        JsonArray results = new JsonArray();
        for (JsonElement value : input.getAsJsonArray()) {
            var row = value.getAsJsonObject();
            JsonObject result = new JsonObject();
            result.add("id", row.get("id").deepCopy());
            try {
                long refund = RefundInput.calculate(row);
                result.addProperty("status", "ok");
                result.addProperty("refund", refund);
            } catch (IllegalArgumentException error) {
                result.addProperty("status", "error");
                result.addProperty("error", error.getMessage());
            }
            results.add(result);
        }
        return results;
    }
    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("요청 JSON 파일을 지정하세요.");
            var input = JsonParser.parseString(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
            System.out.println(process(input));
        } catch (Exception error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }
}
