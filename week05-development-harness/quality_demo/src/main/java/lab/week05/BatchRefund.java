package lab.week05;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class BatchRefund {
    private BatchRefund() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            fail("사용법: BatchRefund <JSON 파일 경로>");
            return;
        }

        JsonElement document;
        try {
            String raw = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            document = RefundInput.JSON.fromJson(raw, JsonElement.class);
        } catch (IOException | InvalidPathException failure) {
            fail("파일을 읽을 수 없습니다: " + failure.getMessage());
            return;
        } catch (JsonParseException failure) {
            fail("올바른 JSON이 아닙니다: " + failure.getMessage());
            return;
        }
        if (document == null || !document.isJsonArray()) {
            fail("최상위 JSON 값은 배열이어야 합니다.");
            return;
        }

        JsonArray results = refundRequests(document.getAsJsonArray());
        System.out.println(RefundInput.JSON.toJson(results));
    }

    private static JsonArray refundRequests(JsonArray requests) {
        JsonArray results = new JsonArray();
        for (JsonElement request : requests) {
            JsonObject result = new JsonObject();
            result.add("id", request.getAsJsonObject().get("id"));
            try {
                long refund = RefundInput.refundFromJson(request);
                result.addProperty("status", "ok");
                result.addProperty("refund", refund);
            } catch (IllegalArgumentException invalidInput) {
                result.addProperty("status", "error");
                result.addProperty("error", invalidInput.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
