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

class BatchResultCheckTest {
    @TempDir Path temporaryDirectory;

    @Test void completeMixedResultsAndEmptyBatchAreAccepted() throws Exception {
        JsonArray expected = expected();
        assertTrue(BatchResultCheck.compare(requests(), expected, expected.deepCopy()).isEmpty());
        assertTrue(BatchResultCheck.compare(new JsonArray(), new JsonArray(), new JsonArray()).isEmpty());
    }

    @Test void missingErrorRowsReportEveryMissingId() throws Exception {
        JsonArray expected = expected();
        JsonArray actual = new JsonArray();
        for (JsonElement row : expected) {
            if (row.getAsJsonObject().get("status").getAsString().equals("ok")) { actual.add(row.deepCopy()); }
        }
        List<String> issues = BatchResultCheck.compare(requests(), expected, actual);
        assertTrue(issues.contains("결과 수 불일치: 요청 8, 결과 3"));
        for (String id : List.of("negative-paid", "text-amount", "boolean-amount", "negative-fee-waived", "invalid-waiver")) {
            assertTrue(issues.contains("결과 누락: id=" + id), id);
        }
    }

    @Test void duplicateAndUnknownIdsFailWithUnchangedResultCount() throws Exception {
        JsonArray expected = expected();
        for (String replacement : List.of("regular", "unrecognized")) {
            JsonArray actual = expected.deepCopy();
            actual.get(1).getAsJsonObject().addProperty("id", replacement);
            List<String> issues = BatchResultCheck.compare(requests(), expected, actual);
            assertEquals(8, actual.size());
            assertTrue(issues.contains("결과 누락: id=negative-paid"));
            assertTrue(issues.contains((replacement.equals("regular") ? "중복 결과 id=" : "알 수 없는 결과 id=") + replacement));
            assertTrue(issues.stream().anyMatch(issue -> issue.startsWith("순서/id 불일치:")));
        }
    }

    @Test void reorderedResultsAreRejectedEvenWhenAllIdsRemain() throws Exception {
        JsonArray expected = expected();
        JsonArray actual = expected.deepCopy();
        JsonElement first = actual.get(0);
        actual.set(0, actual.get(2));
        actual.set(2, first);
        List<String> issues = BatchResultCheck.compare(requests(), expected, actual);
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(issue -> issue.startsWith("순서/id 불일치:")));
    }

    @Test void wrongRefundErrorStatusAndExtraFieldsAreRejected() throws Exception {
        JsonArray expected = expected();
        JsonArray wrongRefund = expected.deepCopy();
        wrongRefund.get(0).getAsJsonObject().addProperty("refund", 7999);
        JsonArray textRefund = expected.deepCopy();
        textRefund.get(0).getAsJsonObject().addProperty("refund", "8000");
        JsonArray wrongError = expected.deepCopy();
        wrongError.get(1).getAsJsonObject().addProperty("error", "다른 오류");
        JsonArray wrongStatus = expected.deepCopy();
        wrongStatus.get(1).getAsJsonObject().addProperty("status", "ok");
        JsonArray extraField = expected.deepCopy();
        extraField.get(0).getAsJsonObject().addProperty("unexpected", true);
        for (JsonArray actual : List.of(wrongRefund, textRefund, wrongError, wrongStatus, extraField)) {
            List<String> issues = BatchResultCheck.compare(requests(), expected, actual);
            assertEquals(1, issues.size());
            assertTrue(issues.get(0).startsWith("결과 내용 불일치:"));
        }
    }

    @Test void refundComparisonPreservesIntegerPrecision() throws Exception {
        JsonArray expected = expected();
        for (String value : List.of("8000.0000000000001", "8000.0", "8e3", "9223372036854775808")) {
            JsonArray actual = RefundInput.JSON.fromJson(
                expected.toString().replace("\"refund\":8000", "\"refund\":" + value), JsonArray.class);
            assertFalse(BatchResultCheck.compare(requests(), expected, actual).isEmpty(), value);
        }
        JsonArray maximumExpected = RefundInput.JSON.fromJson(
            expected.toString().replace("\"refund\":8000", "\"refund\":9223372036854775807"), JsonArray.class);
        JsonArray oneLess = RefundInput.JSON.fromJson(
            maximumExpected.toString().replace("9223372036854775807", "9223372036854775806"), JsonArray.class);
        assertTrue(BatchResultCheck.compare(requests(), maximumExpected, maximumExpected.deepCopy()).isEmpty());
        assertFalse(BatchResultCheck.compare(requests(), maximumExpected, oneLess).isEmpty());
        assertHeld(execute(outputFile(expected.toString().replace("\"refund\":8000", "\"refund\":8000.0000000000001")).toString()),
            "결과 내용 불일치: id=regular");
    }

    @Test void expectedFileMustCorrespondToInputRequests() throws Exception {
        JsonArray wrongExpected = expected();
        wrongExpected.get(0).getAsJsonObject().addProperty("id", "another-request");
        List<String> issues = BatchResultCheck.compare(requests(), wrongExpected, wrongExpected.deepCopy());
        assertTrue(issues.stream().anyMatch(issue -> issue.startsWith("기대 자료의 id·순서 불일치:")));
        assertFalse(BatchResultCheck.compare(requests(), new JsonArray(), new JsonArray()).isEmpty());
    }

    @Test void commandChecksTheSavedFileAndReturnsItsDecision() throws Exception {
        JsonArray actual = expected();
        Path output = outputFile(actual.toString());
        Execution success = execute(output.toString());
        assertEquals(0, success.exitCode(), success.stderr());
        assertTrue(success.stdout().contains("검사 통과: 요청 8개와 결과 8개"));

        actual.get(1).getAsJsonObject().addProperty("id", "regular");
        Files.writeString(output, actual.toString(), StandardCharsets.UTF_8);
        Execution failure = execute(output.toString());
        assertHeld(failure, "중복 결과 id=regular");
        assertTrue(failure.stderr().contains("결과 누락: id=negative-paid"));
    }

    @Test void missingMalformedAndNonArrayOutputCannotPass() throws Exception {
        assertHeld(execute(temporaryDirectory.resolve("missing.json").toString()), "실제 출력 파일을 읽을 수 없습니다:");
        for (String raw : List.of("", "not-json", "{}", "null", "[] []", "[null]")) {
            assertHeld(execute(outputFile(raw).toString()), "완료 보류:");
        }
    }

    @Test void commandRequiresThreePaths() throws Exception {
        assertHeld(JavaProcessFixture.execute(BatchResultCheck.class, temporaryDirectory), "사용법: BatchResultCheck");
        assertHeld(JavaProcessFixture.execute(BatchResultCheck.class, temporaryDirectory, "one", "two"), "사용법: BatchResultCheck");
    }

    private JsonArray requests() throws Exception { return readArray("data/batch-requests.json"); }
    private JsonArray expected() throws Exception { return readArray("data/batch-expected.json"); }
    private JsonArray readArray(String filename) throws Exception {
        return RefundInput.JSON.fromJson(Files.readString(Path.of(filename), StandardCharsets.UTF_8), JsonArray.class);
    }
    private Path outputFile(String raw) throws Exception {
        return Files.writeString(Files.createTempFile(temporaryDirectory, "result-", ".json"), raw, StandardCharsets.UTF_8);
    }
    private Execution execute(String output) throws Exception {
        return JavaProcessFixture.execute(BatchResultCheck.class, temporaryDirectory,
            "data/batch-requests.json", "data/batch-expected.json", output);
    }
    private void assertHeld(Execution execution, String reason) {
        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertTrue(execution.stderr().contains("완료 보류:"), execution.stderr());
        assertTrue(execution.stderr().contains(reason), execution.stderr());
    }
}
