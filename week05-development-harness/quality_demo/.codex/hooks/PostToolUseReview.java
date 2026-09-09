import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Receives command results; business code is never compiled or executed here. */
public class PostToolUseReview {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().create();

    private static JsonElement field(JsonObject object, String snake, String camel) {
        JsonElement value = object.get(snake);
        return value == null || value.isJsonNull() ? object.get(camel) : value;
    }

    public static Integer commandExitCode(JsonObject payload) {
        JsonElement response = field(payload, "tool_response", "toolResponse");
        if (response == null || !response.isJsonObject()) { return null; }
        JsonElement value = field(response.getAsJsonObject(), "exit_code", "exitCode");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("-?(0|[1-9][0-9]*)")) { return null; }
        try { return Integer.valueOf(value.getAsString()); }
        catch (NumberFormatException invalid) { return null; }
    }

    public static JsonObject safeRecord(JsonObject payload) {
        JsonObject record = new JsonObject();
        record.addProperty("recorded_at", Instant.now().toString());
        record.add("tool_name", stringField(payload, "tool_name", "toolName"));
        record.addProperty("exit_code", commandExitCode(payload));
        record.add("turn_id", stringField(payload, "turn_id", "turnId"));
        return record;
    }

    private static JsonElement stringField(JsonObject payload, String snake, String camel) {
        JsonElement value = field(payload, snake, camel);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value : JsonNull.INSTANCE;
    }

    public static JsonObject hookOutput(JsonObject payload) {
        Integer exitCode = commandExitCode(payload);
        if (Integer.valueOf(0).equals(exitCode)) { return new JsonObject(); }
        String message;
        String context;
        if (exitCode == null) {
            message = "명령의 종료 코드를 확인하지 못했습니다.";
            context = "종료 코드가 없으므로 성공으로 보고하지 마세요. 실제 도구 결과에서 실행 완료 여부와 오류를 확인하세요. "
                    + "아직 실행 중이라면 완료 결과를 확인하고, 결과 형식이 다르면 Hook의 종료 코드 읽기를 점검하세요.";
        } else {
            message = "명령이 종료 코드 " + exitCode + "로 실패했습니다.";
            context = "실제 출력을 읽고 실행 환경 문제인지 기능의 기대값 불일치인지 구분하세요. 원인을 수정한 뒤 같은 검사를 다시 실행하세요. "
                    + "검사를 삭제하거나 요구에 근거한 기대값을 약화하지 마세요. 해결할 수 없으면 같은 명령을 반복하지 말고 원인과 필요한 정보를 보고하세요.";
        }
        JsonObject result = new JsonObject();
        result.addProperty("systemMessage", message);
        JsonObject specific = new JsonObject();
        specific.addProperty("hookEventName", "PostToolUse");
        specific.addProperty("additionalContext", context);
        result.add("hookSpecificOutput", specific);
        return result;
    }

    public static int run(Reader input, PrintWriter output, PrintWriter errors) {
        final JsonObject payload;
        try {
            JsonElement parsed = JSON.fromJson(input, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) { throw new IllegalArgumentException("object required"); }
            payload = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalArgumentException invalid) {
            errors.println("Invalid hook payload: JSON object required."); errors.flush(); return 2;
        }
        // Local logs retain event metadata only. Never copy command input or output.
        try {
            JsonElement cwdValue = payload.get("cwd");
            Path cwd = Path.of(cwdValue != null && cwdValue.isJsonPrimitive()
                    && cwdValue.getAsJsonPrimitive().isString() ? cwdValue.getAsString() : ".").toAbsolutePath().normalize();
            Path log = cwd.resolveSibling(".local").resolve("raw/hook-events.jsonl");
            Files.createDirectories(log.getParent());
            Files.writeString(log, JSON.toJson(safeRecord(payload)) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException logFailure) {
            // Feedback remains useful when a local metadata log is unavailable.
            errors.println("Hook event metadata could not be recorded."); errors.flush();
        }
        output.println(JSON.toJson(hookOutput(payload))); output.flush(); return 0;
    }

    public static void main(String[] args) {
        int code = run(new InputStreamReader(System.in, StandardCharsets.UTF_8),
                new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)),
                new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
        if (code != 0) { System.exit(code); }
    }
}
