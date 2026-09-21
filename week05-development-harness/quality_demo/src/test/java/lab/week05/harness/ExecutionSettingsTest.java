package lab.week05.harness;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import lab.week05.ProcessRunner;
import lab.week05.harness.DevelopmentHarness.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionSettingsTest {
    @TempDir Path temp;
    private Path request(String execution) throws Exception {
        Files.writeString(temp.resolve("task.md"), "지원 요청 표시 규칙을 확인하세요.");
        String fields = execution == null ? "" : ",\"execution\":" + execution;
        Path file = temp.resolve("work.json");
        Files.writeString(file, "{\"workspace\":\".\",\"task\":\"task.md\","
            + "\"verify\":{\"windows\":[\"test\",\"acceptance\"],\"posix\":[\"test\",\"acceptance\"]}"
            + fields + "}");
        return file;
    }
    private static final String EXPLICIT = "{\"model\":\"gpt-6-astra\",\"reasoningEffort\":\"low\"}";
    @Test void explicitSettingsReachInitialAndRepairCalls() throws Exception {
        WorkRequest work = WorkRequest.load(request(EXPLICIT));
        List<List<String>> commands = new ArrayList<>();
        List<Path> checks = new ArrayList<>();
        StepRunner runner = HarnessCli.processRunner(work, temp,
            (command, workspace, input, timeout) -> {
                commands.add(command);
                assertEquals(work.workspace(), workspace);
                assertEquals(work.timeoutSeconds(), timeout);
                assertTrue(input.contains("지원 요청 표시"));
                if (commands.size() == 2) assertTrue(input.contains("빈 제목"));
                return new ProcessRunner.Result(0, "모델 완료", false);
            },
            (command, workspace, timeout, reports) -> {
                checks.add(reports);
                assertEquals(work.verify(), command);
                return checks.size() == 1 ? new StepResult(ResultKind.FAILED, 1, "빈 제목 실패")
                    : new StepResult(ResultKind.OK, 0, "통과");
            });
        Outcome outcome = new DevelopmentHarness().run(work, runner);
        assertEquals(Status.SUCCEEDED, outcome.status());
        assertEquals(2, outcome.attempts());
        assertEquals(2, commands.size());
        for (List<String> command : commands) {
            assertEquals("gpt-6-astra", command.get(command.indexOf("--model") + 1));
            assertTrue(command.contains("model_reasoning_effort=low"));
            assertEquals("-", command.get(command.size() - 1));
        }
        assertTrue(commands.get(0).contains(temp.resolve("agent-1.txt").toString()));
        assertTrue(commands.get(1).contains(temp.resolve("agent-2.txt").toString()));
        assertEquals(List.of(temp.resolve("check-1"), temp.resolve("check-2")), checks);
    }
    @Test void omittedSettingsKeepDefaultCommandAndNormalFlow() throws Exception {
        WorkRequest work = WorkRequest.load(request(null));
        assertNull(work.execution());
        List<Stage> calls = new ArrayList<>();
        StepRunner runner = HarnessCli.processRunner(work, temp,
            (command, workspace, input, timeout) -> {
                calls.add(Stage.EXECUTE);
                assertFalse(command.contains("--model"));
                assertFalse(command.stream().anyMatch(value -> value.startsWith("model_reasoning_effort=")));
                return new ProcessRunner.Result(0, "완료", false);
            }, (command, workspace, timeout, reports) -> {
                calls.add(Stage.VERIFY);
                return new StepResult(ResultKind.OK, 0, "통과");
            });
        assertEquals(Status.SUCCEEDED, new DevelopmentHarness().run(work, runner).status());
        assertEquals(List.of(Stage.EXECUTE, Stage.VERIFY), calls);
    }
    @Test void resultRecordsRequestedSettingsWithoutClaimingObservedModel() throws Exception {
        for (String value : Arrays.asList(null, EXPLICIT)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertEquals(0, HarnessCli.run(request(value), true, temp.resolve("reports"),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)));
            JsonObject report = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject settings = report.getAsJsonObject("requestedExecution");
            assertEquals(value == null ? "DEFAULTS" : "EXPLICIT", settings.get("source").getAsString());
            assertEquals(value != null, settings.has("model"));
            if (value != null) {
                assertEquals("gpt-6-astra", settings.get("model").getAsString());
                assertEquals("low", settings.get("reasoningEffort").getAsString());
            }
            assertEquals("SIMULATED_RESPONSE", report.get("mode").getAsString());
        }
    }
    @Test void invalidOrPartialSettingsStopDuringPreparation() throws Exception {
        for (String value : List.of("null", "{}", "[]", "{\"model\":\"gpt-6-astra\"}",
                "{\"reasoningEffort\":\"low\"}", EXPLICIT.replace("low", "fast"),
                EXPLICIT.replace("gpt-6-astra", "bad & command"), EXPLICIT.replace("gpt-6-astra", ""),
                EXPLICIT.replace("\"low\"", "3"), EXPLICIT.replace("reasoningEffort", "effort"))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertEquals(2, HarnessCli.run(request(value), false, temp.resolve("reports"),
                new PrintStream(bytes, true, StandardCharsets.UTF_8)));
            JsonObject report = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("PREPARE", report.getAsJsonObject("outcome").get("stage").getAsString());
            assertEquals(0, report.getAsJsonObject("outcome").get("attempts").getAsInt());
            assertEquals("UNAVAILABLE", report.getAsJsonObject("requestedExecution").get("source").getAsString());
        }
    }
}
