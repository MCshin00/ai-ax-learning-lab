package lab.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopHookTest {
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final String PASS_XML = "<testsuite tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\">"
        + "<testcase name=\"unknownServiceDoesNotInventStatus()\"/></testsuite>";
    private static final String FAIL_XML = "<testsuite tests=\"1\" skipped=\"0\" failures=\"1\" errors=\"0\">"
        + "<testcase name=\"unknownServiceDoesNotInventStatus()\">"
        + "<failure message=\"자료에 없는 서비스는 없음이어야 하지만 상태가 생성됨\"/></testcase></testsuite>";

    @TempDir Path tempDir;

    @Test void passingAppTestReturnsEmptyDecisionAndRecordsTheResult() throws Exception {
        assertEquals("{}", process(false, "pass", reportRunner(PASS_XML, 0)));
        String record = Files.readString(recordFile());
        assertTrue(record.contains("\"hook_event_name\":\"Stop\""));
        assertTrue(record.contains("\"turn_id\":\"turn-123\""));
        assertTrue(record.contains("\"check_status\":\"pass\""));
        assertFalse(record.contains("private example"));
    }

    @Test void functionalFailureReportsTheProblemWithoutRequestingAnotherCorrection() throws Exception {
        String response = process(false, "first-failure", reportRunner(FAIL_XML, 1));
        assertTrue(response.contains("\"continue\":false"));
        assertTrue(response.contains("문의 앱 검사 실패"));
        assertTrue(response.contains("unknownServiceDoesNotInventStatus"));
        assertTrue(response.contains("자료에 없는 서비스는 없음이어야 하지만 상태가 생성됨"));
        assertFalse(response.contains("\"decision\":\"block\""));
        assertTrue(Files.readString(recordFile()).contains("\"check_status\":\"failure\""));
    }

    @Test void continuedTurnAlsoReportsTheFailureWithoutRequestingAnotherCorrection() throws Exception {
        String response = process(true, "retry-failure", reportRunner(FAIL_XML, 1));
        assertTrue(response.contains("\"continue\":false"));
        assertTrue(response.contains("문의 앱 검사 실패"));
        assertTrue(response.contains("unknownServiceDoesNotInventStatus"));
        assertFalse(response.contains("\"decision\":\"block\""));
    }

    @Test void aBuildErrorWithoutFreshXmlIsUnavailableEvenIfAnOldReportPassed() throws Exception {
        Path oldReport = projectRoot().resolve("build/test-results/test/TEST-lab.desk.InquiryTest.xml");
        Files.createDirectories(oldReport.getParent());
        Files.writeString(oldReport, PASS_XML);

        String response = process(false, "unavailable", (root, build, log, socket, timeout) ->
            new StopHook.RunResult(1, false, "Gradle을 시작할 수 없습니다"));
        assertTrue(response.contains("\"continue\":false"));
        assertTrue(response.contains("문의 앱 검사 실행 불가"));
        assertTrue(Files.readString(recordFile()).contains("\"check_status\":\"unavailable\""));
    }

    @Test void aTimedOutRunIsUnavailable() throws Exception {
        String response = process(false, "timeout", (root, build, log, socket, timeout) -> {
            assertEquals(StopHook.TEST_TIMEOUT, timeout);
            return new StopHook.RunResult(-1, true, null);
        });
        assertTrue(response.contains(StopHook.TEST_TIMEOUT.toSeconds() + "초"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void aSuccessfulBuildWithoutTestsIsUnavailable() throws Exception {
        String response = process(false, "no-tests", reportRunner(
            "<testsuite tests=\"0\" skipped=\"0\" failures=\"0\" errors=\"0\"/>", 0));
        assertTrue(response.contains("실행된 문의 앱 검사가 없습니다"));
    }

    @Test void noSourceBuildDoesNotUseAnOldReport() throws Exception {
        Path oldReport = projectRoot().resolve("build/test-results/test/TEST-lab.desk.InquiryTest.xml");
        Files.createDirectories(oldReport.getParent());
        Files.writeString(oldReport, PASS_XML);
        String response = process(false, "no-source", (root, build, log, socket, timeout) ->
            new StopHook.RunResult(0, false, null));
        assertTrue(response.contains("새 문의 앱 검사 결과 파일이 없습니다"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void allSkippedTestsAreUnavailable() throws Exception {
        String response = process(false, "skipped", reportRunner(
            "<testsuite tests=\"1\" skipped=\"1\" failures=\"0\" errors=\"0\"/>", 0));
        assertTrue(response.contains("실행된 문의 앱 검사가 없습니다"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void nonzeroExitWithPassingXmlIsUnavailable() throws Exception {
        String response = process(false, "nonzero", reportRunner(PASS_XML, 1));
        assertTrue(response.contains("Gradle 종료 코드가 1"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void invalidXmlIsUnavailable() throws Exception {
        String response = process(false, "invalid-xml", reportRunner("<unexpected/>", 0));
        assertTrue(response.contains("결과를 읽을 수 없습니다"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void anErrorInAnotherSuiteReportsItsCaseAndLocation() throws Exception {
        StopHook.Runner passing = reportRunner(PASS_XML, 0);
        String response = process(false, "multiple", (root, build, log, socket, timeout) -> {
            passing.run(root, build, log, socket, timeout);
            Path report = build.resolve("test-results/test/TEST-lab.desk.StatusTest.xml");
            try {
                Files.writeString(report,
                    "<testsuite tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"1\">"
                    + "<testcase name=\"lookupFailurePreservesCause\"><error message=\"조회 원인 누락\"/>"
                    + "</testcase></testsuite>");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new StopHook.RunResult(1, false, null);
        });
        assertTrue(response.contains("lookupFailurePreservesCause"));
        assertTrue(response.contains("조회 원인 누락"));
        assertTrue(response.contains("TEST-lab.desk.StatusTest.xml"));
        assertTrue(response.contains("\"continue\":false"));
    }

    @Test void anInvalidEventDoesNotStartTheRunner() {
        String input = "{\"hook_event_name\":\"SessionEnd\",\"turn_id\":\"turn-123\",\"stop_hook_active\":false}";
        assertThrows(IllegalArgumentException.class, () -> StopHook.process(input, recordFile(), projectRoot(), null,
            (root, build, log, socket, timeout) -> { throw new AssertionError("Runner must not start"); }, "invalid", NOW));
        assertFalse(Files.exists(recordFile()));
    }

    private String process(boolean active, String runId, StopHook.Runner runner) throws IOException {
        String input = "{\"hook_event_name\":\"Stop\",\"turn_id\":\"turn-123\","
            + "\"stop_hook_active\":" + active + ",\"last_assistant_message\":\"private example\"}";
        return StopHook.process(input, recordFile(), projectRoot(), null, runner, runId, NOW);
    }

    private StopHook.Runner reportRunner(String xml, int exitCode) {
        return (root, build, log, socket, timeout) -> {
            Path report = build.resolve("test-results/test/TEST-lab.desk.InquiryTest.xml");
            try {
                Files.createDirectories(report.getParent());
                Files.writeString(report, xml);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new StopHook.RunResult(exitCode, false, null);
        };
    }

    private Path projectRoot() { return tempDir.resolve("project"); }
    private Path recordFile() { return tempDir.resolve("last-hook-event.json"); }
}
