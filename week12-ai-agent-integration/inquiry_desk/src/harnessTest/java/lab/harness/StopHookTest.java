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
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopHookTest {
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final String FAIL_XML = "<testsuite tests=\"1\" skipped=\"0\" failures=\"1\" errors=\"0\">"
        + "<testcase name=\"unknownServiceDoesNotInventStatus()\">"
        + "<failure message=\"자료에 없는 서비스는 없음이어야 하지만 상태가 생성됨\"/></testcase></testsuite>";

    @TempDir Path tempDir;

    @Test void passingAppTestReturnsEmptyDecisionAndRecordsTheResult() throws Exception {
        assertEquals("{}", process(false, "pass", (root, build, log, socket, timeout) ->
            new StopHook.RunResult(0, false, null)));
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
        assertTrue(response.contains(Path.of(".local/harness/runs/first-failure/build/test-results/test")
            .toString().replace("\\", "\\\\")));
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
        assertFalse(response.contains("\"decision\":\"block\""));
        assertTrue(Files.readString(recordFile()).contains("\"hook_action\":\"stopped\""));
    }

    @Test void aCheckWithoutAResultStopsEvenOnTheFirstTime() throws Exception {
        // 검사의 결과를 얻지 못하면 원인이 환경인지 코드인지 Hook이 가리지 못한다. 고치라고 하지 않고 멈춰 로그 위치를 알린다.
        String response = process(false, "unavailable-first", (root, build, log, socket, timeout) ->
            new StopHook.RunResult(1, false, null));
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

    @Test void aWorkerThatStoppedWithAQuestionIsNotChecked() throws Exception {
        assertNotChecked("{\"status\":\"stopped\",\"summary\":\"대소문자 처리가 계획에 없습니다\",\"questions\":" + QUESTIONS + "}");
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
