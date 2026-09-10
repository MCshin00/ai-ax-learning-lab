package lab.week05;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lab.week05.JavaProcessFixture.Execution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BatchRefundTest {
    @TempDir Path temporaryDirectory;

    @Test void providedBatchMatchesEveryRequestAndExpectedResult() throws Exception {
        Execution execution = execute("data/batch-requests.json");
        JsonArray actual = successfulResults(execution);
        JsonArray requests = readArray(Path.of("data/batch-requests.json"));
        JsonArray expected = readArray(Path.of("data/batch-expected.json"));

        List<String> issues = BatchResultCheck.compare(requests, expected, actual);
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test void emptyArrayIsSuccessful() throws Exception {
        Execution execution = execute(inputFile("[]").toString());
        assertTrue(successfulResults(execution).isEmpty());
        assertEquals("[]", execution.stdout().strip());
    }

    @Test void malformedJsonNeverPrintsPartialResults() throws Exception {
        for (String raw : List.of("not-json", "[] []", "[{id:1}]",
                "[{\"id\":\"first\",\"paid\":10000,\"fee\":2000},")) {
            assertFailure(execute(inputFile(raw).toString()), "올바른 JSON이 아닙니다:");
        }
    }

    @Test void nonArrayDocumentsFail() throws Exception {
        for (String raw : List.of("{}", "null", "1", "true", "\"text\"", "")) {
            assertFailure(execute(inputFile(raw).toString()), "최상위 JSON 값은 배열이어야 합니다.");
        }
    }

    @Test void missingFileAndDirectoryFail() throws Exception {
        assertFailure(execute(temporaryDirectory.resolve("missing.json").toString()), "파일을 읽을 수 없습니다:");
        assertFailure(execute(temporaryDirectory.toString()), "파일을 읽을 수 없습니다:");
    }

    @Test void exactlyOneFileArgumentIsRequired() throws Exception {
        assertFailure(execute(), "사용법: BatchRefund");
        assertFailure(execute("first.json", "second.json"), "사용법: BatchRefund");
    }

    @Test void batchPreservesADefaultsValidationOrderAndAmountBoundaries() throws Exception {
        String raw = """
            [
              {"id":"default-waiver","paid":10000,"fee":2000},
              {"id":"both-invalid","paid":1000,"fee":true,"fee_waived":0},
              {"id":"largest","paid":9223372036854775807,"fee":0},
              {"id":"zero","paid":0,"fee":0,"fee_waived":true}
            ]
            """;
        JsonArray expected = RefundInput.JSON.fromJson("""
            [
              {"id":"default-waiver","status":"ok","refund":8000},
              {"id":"both-invalid","status":"error","error":"금액은 0 이상의 정수여야 합니다."},
              {"id":"largest","status":"ok","refund":9223372036854775807},
              {"id":"zero","status":"ok","refund":0}
            ]
            """, JsonArray.class);
        assertEquals(expected, successfulResults(execute(inputFile(raw).toString())));
    }

    private Path inputFile(String raw) throws Exception {
        Path path = Files.createTempFile(temporaryDirectory, "input-", ".json");
        return Files.writeString(path, raw, StandardCharsets.UTF_8);
    }

    private JsonArray readArray(Path path) throws Exception {
        return RefundInput.JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonArray.class);
    }

    private JsonArray successfulResults(Execution execution) {
        assertEquals(0, execution.exitCode(), execution.stderr());
        assertEquals(1L, execution.stdout().lines().count(), "표준 출력은 JSON 한 줄이어야 합니다.");
        JsonElement output = RefundInput.JSON.fromJson(execution.stdout(), JsonElement.class);
        assertNotNull(output);
        assertTrue(output.isJsonArray());
        return output.getAsJsonArray();
    }

    private void assertFailure(Execution execution, String cause) {
        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout(), "파일 전체 실패에는 결과 배열을 출력하지 않습니다.");
        assertTrue(execution.stderr().contains(cause), execution.stderr());
    }

    private Execution execute(String... arguments) throws Exception {
        return JavaProcessFixture.execute(BatchRefund.class, temporaryDirectory, arguments);
    }
}
