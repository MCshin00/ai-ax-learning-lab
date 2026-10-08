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
        assertTrue(record.contains("\"hook_action\":\"none\""));
        assertFalse(record.contains("private example"));
    }

    // 이 검사와 다음 검사는 stop_hook_active의 값마다 Hook이 돌려주는 응답을 확인한다.
    // 실제 Codex가 응답을 받아 이어서 실행하고 다음 사건에 true를 넣어 보내는 과정은 여기서 확인하지 않는다.
    @Test void aFailureInATurnNotContinuedByTheHookAsksToFixIt() throws Exception {
        String response = process(false, "first-failure", reportRunner(FAIL_XML, 1));
        assertTrue(response.contains("\"decision\":\"block\""));
        assertFalse(response.contains("\"continue\":false"));
        assertTrue(response.contains("문의 앱 검사 실패"));
        assertTrue(response.contains("unknownServiceDoesNotInventStatus"));
        assertTrue(response.contains("자료에 없는 서비스는 없음이어야 하지만 상태가 생성됨"));
        // 고칠 때 지킬 선을 함께 알린다.
        assertTrue(response.contains("기대값을 바꾸거나"));
        assertTrue(response.contains("멈춰 질문을 돌려주세요"));
        String record = Files.readString(recordFile());
        assertTrue(record.contains("\"check_status\":\"failure\""));
        assertTrue(record.contains("\"hook_action\":\"fix_once\""));
    }

    @Test void aFailureInATurnContinuedByTheHookStopsWithoutAskingAgain() throws Exception {
        String response = process(true, "retry-failure", reportRunner(FAIL_XML, 1));
        assertTrue(response.contains("\"continue\":false"));
        assertTrue(response.contains("문의 앱 검사 실패"));
        assertTrue(response.contains("unknownServiceDoesNotInventStatus"));
        assertFalse(response.contains("\"decision\":\"block\""));
        assertTrue(Files.readString(recordFile()).contains("\"hook_action\":\"stopped\""));
    }

    @Test void aCheckWithoutAResultStopsEvenOnTheFirstTime() throws Exception {
        // 검사의 결과를 얻지 못하면 원인이 환경인지 코드인지 Hook이 가리지 못한다. 고치라고 하지 않고 멈춰 로그 위치를 알린다.
        String response = process(false, "unavailable-first", (root, build, log, socket, timeout) ->
            new StopHook.RunResult(1, false, "Gradle을 시작할 수 없습니다"));
        assertTrue(response.contains("\"continue\":false"));
        assertFalse(response.contains("\"decision\":\"block\""));
        assertTrue(Files.readString(recordFile()).contains("\"hook_action\":\"stopped\""));
    }

    private static final String QUESTIONS = "[{\"input\":\"vpn\",\"options\":[{\"choice\":\"구별\",\"result\":\"없음\"}]}]";

    // 사건의 last_assistant_message는 문자열이다. 작업자의 결과 JSON이 그 안에 한 번 더 문자열로 들어간다.
    private static String eventWith(String lastMessage) {
        String escaped = lastMessage.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"hook_event_name\":\"Stop\",\"turn_id\":\"turn-q\",\"stop_hook_active\":false,"
            + "\"last_assistant_message\":\"" + escaped + "\"}";
    }

    private void assertNotChecked(String lastMessage) throws IOException {
        Files.deleteIfExists(recordFile());
        String response = StopHook.process(eventWith(lastMessage), recordFile(), projectRoot(), null,
            (root, build, log, socket, timeout) -> { throw new AssertionError("검사를 실행하면 안 됩니다: " + lastMessage); },
            "question", NOW);
        assertEquals("{}", response);
        String record = Files.readString(recordFile());
        assertTrue(record.contains("\"check_status\":\"question\""));
        assertTrue(record.contains("\"hook_action\":\"skipped\""));
    }

    private void assertChecked(String lastMessage, String runId) throws IOException {
        Files.deleteIfExists(recordFile());
        assertEquals("{}", StopHook.process(eventWith(lastMessage), recordFile(), projectRoot(), null,
            reportRunner(PASS_XML, 0), runId, NOW));
        assertTrue(Files.readString(recordFile()).contains("\"check_status\":\"pass\""), lastMessage);
    }

    @Test void aWorkerThatStoppedWithAQuestionIsNotChecked() throws Exception {
        assertNotChecked("{\"status\":\"stopped\",\"summary\":\"대소문자 처리가 계획에 없습니다\",\"questions\":" + QUESTIONS + "}");
        // 같은 뜻의 JSON이면 줄바꿈·공백·이스케이프 표기가 달라도 같게 읽는다.
        assertNotChecked("\n{\"status\":\n  \"stopped\",\n  \"questions\": " + QUESTIONS + "\n}\n");
        assertNotChecked("{\"questions\":" + QUESTIONS + ",\"status\":\"\\u0073topped\"}");
    }

    @Test void onlyAStoppedResultWithQuestionsSkipsTheCheck() throws Exception {
        // 끝났다고 한 결과.
        assertChecked("{\"status\":\"done\",\"questions\":[]}", "done");
        // 멈췄다고 했지만 질문이 없는 결과. 실행 관리도 이 결과를 받아들이지 않는다.
        assertChecked("{\"status\":\"stopped\",\"questions\":[]}", "no-questions");
        // 결과 JSON이 아닌 글. 그 낱말이 설명으로 나오거나, 글로 질문을 했거나, JSON을 글 안에 넣은 경우.
        assertChecked("status가 stopped인 경우를 구현했습니다", "prose");
        assertChecked("대소문자를 구별할까요?", "prose-question");
        assertChecked("결과입니다: {\"status\":\"stopped\",\"questions\":" + QUESTIONS + "}", "wrapped");
        // JSON 문법에 맞지 않는 표기는 결과로 읽지 않는다.
        assertChecked("{\"status\":\"\\u+073topped\",\"questions\":" + QUESTIONS + "}", "bad-escape");
        assertChecked("{\"status\":\"stopped\",\"questions\":" + QUESTIONS + ",\"n\":01}", "bad-number");
        // status가 다른 항목 안에만 있는 경우.
        assertChecked("{\"status\":\"done\",\"note\":{\"status\":\"stopped\",\"questions\":" + QUESTIONS + "},\"questions\":[]}", "nested");
    }

    @Test void anEventThatIsNotValidJsonIsRejected() {
        String truncated = "{\"hook_event_name\":\"Stop\",\"turn_id\":\"turn-123\",\"stop_hook_active\":false";
        assertThrows(IllegalArgumentException.class, () -> StopHook.process(truncated, recordFile(), projectRoot(), null,
            (root, build, log, socket, timeout) -> { throw new AssertionError("Runner must not start"); }, "broken", NOW));
        String wrongType = "{\"hook_event_name\":\"Stop\",\"turn_id\":\"turn-123\",\"stop_hook_active\":\"false\"}";
        assertThrows(IllegalArgumentException.class, () -> StopHook.process(wrongType, recordFile(), projectRoot(), null,
            (root, build, log, socket, timeout) -> { throw new AssertionError("Runner must not start"); }, "wrong-type", NOW));
        assertFalse(Files.exists(recordFile()));
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
        assertTrue(response.contains("\"decision\":\"block\""));
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
