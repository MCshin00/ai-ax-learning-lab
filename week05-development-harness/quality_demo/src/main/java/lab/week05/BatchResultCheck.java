package lab.week05;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BatchResultCheck {
    private BatchResultCheck() {}

    public static void main(String[] args) {
        if (args.length != 3) {
            fail(List.of("사용법: BatchResultCheck <입력 파일> <기대 결과 파일> <실제 출력 파일>"));
            return;
        }
        try {
            JsonArray requests = readArray(args[0], "입력");
            JsonArray expected = readArray(args[1], "기대 결과");
            JsonArray actual = readArray(args[2], "실제 출력");
            List<String> issues = compare(requests, expected, actual);
            if (!issues.isEmpty()) {
                fail(issues);
                return;
            }
            System.out.println("검사 통과: 요청 " + requests.size() + "개와 결과 " + actual.size()
                + "개의 id·순서·내용이 일치합니다.");
        } catch (IllegalArgumentException failure) {
            fail(List.of(failure.getMessage()));
        }
    }

    // BatchRefundTest와 저장된 출력 검사에서 같은 비교 기준을 사용한다.
    public static List<String> compare(JsonArray requests, JsonArray expected, JsonArray actual) {
        List<String> issues = new ArrayList<>();
        if (requests.size() != expected.size()) {
            return List.of("기대 결과 수 불일치: 요청 " + requests.size() + ", 기대 결과 " + expected.size());
        }
        Set<JsonElement> requestIds = new LinkedHashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            JsonElement requestId = idOf(requests.get(index));
            if (requestId == null) {
                issues.add("입력 " + (index + 1) + "번째 요청의 id를 확인할 수 없습니다.");
            } else {
                requestIds.add(requestId);
                if (!requestId.equals(idOf(expected.get(index)))) {
                    issues.add("기대 자료의 id·순서 불일치: " + (index + 1) + "번째 요청 id=" + label(requestId));
                }
            }
        }
        if (!issues.isEmpty()) { return List.copyOf(issues); }

        if (requests.size() != actual.size()) {
            issues.add("결과 수 불일치: 요청 " + requests.size() + ", 결과 " + actual.size());
        }
        Set<JsonElement> resultIds = new LinkedHashSet<>();
        for (int index = 0; index < actual.size(); index++) {
            JsonElement resultId = idOf(actual.get(index));
            if (resultId == null) {
                issues.add("결과 " + (index + 1) + "번째 행의 id를 확인할 수 없습니다.");
                continue;
            }
            if (!resultIds.add(resultId)) { issues.add("중복 결과 id=" + label(resultId)); }
            if (!requestIds.contains(resultId)) { issues.add("알 수 없는 결과 id=" + label(resultId)); }
        }
        for (JsonElement requestId : requestIds) {
            if (!resultIds.contains(requestId)) { issues.add("결과 누락: id=" + label(requestId)); }
        }
        for (int index = 0; index < Math.min(requests.size(), actual.size()); index++) {
            JsonElement requestId = idOf(requests.get(index));
            JsonElement resultId = idOf(actual.get(index));
            if (!Objects.equals(requestId, resultId)) {
                issues.add("순서/id 불일치: " + (index + 1) + "번째 요청 id=" + label(requestId)
                    + ", 결과 id=" + label(resultId));
            } else if (!sameResult(expected.get(index), actual.get(index))) {
                issues.add("결과 내용 불일치: id=" + label(requestId)
                    + " (status·refund·error 및 필드 구성을 기대 결과와 대조하세요.)");
            }
        }
        return List.copyOf(issues);
    }

    private static boolean sameResult(JsonElement expected, JsonElement actual) {
        if (!expected.equals(actual)) { return false; }
        JsonElement expectedRefund = expected.getAsJsonObject().get("refund");
        if (expectedRefund == null) { return true; }
        // Gson의 숫자 동등 비교에서 잃을 수 있는 정밀도를 정수 금액 계약으로 확인한다.
        Long expectedAmount = integerAmount(expectedRefund);
        return expectedAmount != null
            && expectedAmount.equals(integerAmount(actual.getAsJsonObject().get("refund")));
    }

    private static Long integerAmount(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]*")) { return null; }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static JsonElement idOf(JsonElement row) {
        if (row == null || !row.isJsonObject()) { return null; }
        JsonElement id = row.getAsJsonObject().get("id");
        return id == null || id.isJsonNull() ? null : id;
    }

    private static String label(JsonElement id) {
        if (id == null) { return "없음"; }
        return id.isJsonPrimitive() ? id.getAsString() : id.toString();
    }

    private static JsonArray readArray(String filename, String label) {
        final String raw;
        try {
            raw = Files.readString(Path.of(filename), StandardCharsets.UTF_8);
        } catch (IOException | InvalidPathException failure) {
            throw new IllegalArgumentException(label + " 파일을 읽을 수 없습니다: " + failure.getMessage());
        }
        final JsonElement document;
        try {
            document = RefundInput.JSON.fromJson(raw, JsonElement.class);
        } catch (JsonParseException failure) {
            throw new IllegalArgumentException(label + " JSON을 해석할 수 없습니다: " + failure.getMessage());
        }
        if (document == null) { throw new IllegalArgumentException(label + " 내용이 없습니다."); }
        if (!document.isJsonArray()) { throw new IllegalArgumentException(label + "은 JSON 배열이어야 합니다."); }
        return document.getAsJsonArray();
    }

    private static void fail(List<String> issues) {
        System.err.println("완료 보류: 입력과 결과의 대응을 확인하지 못했습니다.");
        issues.forEach(issue -> System.err.println("- " + issue));
        System.exit(1);
    }
}
