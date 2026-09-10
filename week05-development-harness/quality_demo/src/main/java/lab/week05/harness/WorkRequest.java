package lab.week05.harness;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 요청 파일의 위치를 기준으로 업무 자료와 검사 명령을 준비한다. */
public record WorkRequest(Path workspace, Path task, List<Path> context,
                          List<String> verify, int maxRepairs, int timeoutSeconds) {
    public static WorkRequest load(Path file) throws Exception {
        Path requestFile = file.toAbsolutePath().normalize();
        JsonElement parsed = JsonParser.parseString(Files.readString(requestFile, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("요청 파일은 JSON 객체여야 합니다.");
        JsonObject data = parsed.getAsJsonObject();
        Path workspace = requestFile.getParent().resolve(text(data.get("workspace"), "workspace")).normalize();
        Path task = workspace.resolve(text(data.get("task"), "task")).normalize();
        List<Path> context = strings(data.get("context"), "context", false).stream()
            .map(value -> workspace.resolve(value).normalize()).toList();
        JsonElement commands = data.get("verify");
        if (commands == null || !commands.isJsonObject()) {
            throw new IllegalArgumentException("완료를 판정할 verify 검사 명령이 필요합니다.");
        }
        String platform = System.getProperty("os.name").startsWith("Windows") ? "windows" : "posix";
        List<String> verify = strings(commands.getAsJsonObject().get(platform), "verify." + platform, true);
        return new WorkRequest(workspace, task, context, verify,
            integer(data, "maxRepairs", 1, 0, 1), integer(data, "timeoutSeconds", 600, 1, Integer.MAX_VALUE));
    }
    public String prompt() throws Exception {
        if (!Files.isDirectory(workspace)) throw new IllegalArgumentException("실제 작업 폴더가 필요합니다.");
        if (verify == null || verify.isEmpty() || verify.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("완료를 판정할 검사 명령이 필요합니다.");
        }
        if (maxRepairs < 0 || maxRepairs > 1 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("이번 실습은 복구 0~1회와 양수 시간 제한을 사용합니다.");
        }
        StringBuilder prompt = new StringBuilder("작업 요구\n").append(read(task, "작업 요구"));
        for (Path path : context) {
            prompt.append("\n\n기준: ").append(workspace.relativize(path))
                .append('\n').append(read(path, "맥락"));
        }
        return prompt.toString();
    }
    private static String read(Path path, String name) throws Exception {
        String body = Files.readString(path, StandardCharsets.UTF_8);
        if (body.isBlank()) throw new IllegalArgumentException(name + " 파일의 내용이 필요합니다.");
        return body;
    }
    private static String text(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(name + "에 비어 있지 않은 문자열이 필요합니다.");
        }
        return value.getAsString();
    }
    private static List<String> strings(JsonElement value, String name, boolean required) {
        if (value == null && !required) return List.of();
        if (value == null || !value.isJsonArray() || (required && value.getAsJsonArray().isEmpty())) {
            throw new IllegalArgumentException(name + "에 명령 또는 파일 목록이 필요합니다.");
        }
        return value.getAsJsonArray().asList().stream().map(item -> text(item, name)).toList();
    }
    private static int integer(JsonObject data, String name, int fallback, int min, int max) {
        if (!data.has(name)) return fallback;
        JsonElement value = data.get(name);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || !value.getAsString().matches("0|[1-9][0-9]*")) throw new NumberFormatException();
            int number = Integer.parseInt(value.getAsString());
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + "의 허용 범위는 " + min + "~" + max + " 정수입니다.");
        }
    }
}
