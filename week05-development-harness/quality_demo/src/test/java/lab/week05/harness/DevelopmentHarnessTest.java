package lab.week05.harness;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import lab.week05.harness.DevelopmentHarness.*;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentHarnessTest {
    @TempDir Path temp;
    private WorkRequest request(int repairs) throws Exception {
        Path task = temp.resolve("task.md"), context = temp.resolve("rules.md");
        Files.writeString(task, "모든 요청의 결과를 남기세요.");
        Files.writeString(context, "오류 행도 처리한 결과입니다.");
        return new WorkRequest(temp, task, List.of(context), List.of("verify"), repairs, 10);
    }
    private StepResult ok() { return new StepResult(ResultKind.OK, 0, "완료"); }
    private StepResult failed() { return new StepResult(ResultKind.FAILED, 1, "누락 id: invalid-waiver"); }

    @Test void normalWorkRequiresItsFollowingVerification() throws Exception {
        List<Stage> calls = new ArrayList<>();
        Outcome outcome = new DevelopmentHarness().run(request(1), (stage, input, attempt) -> {
            calls.add(stage);
            if (stage == Stage.VERIFY) assertEquals("", input);
            return ok();
        });
        assertEquals(List.of(Stage.EXECUTE, Stage.VERIFY), calls);
        assertEquals(Status.SUCCEEDED, outcome.status());
        assertEquals(1, outcome.attempts());
    }
    @Test void repairKeepsTaskAndRulesAndUsesNewCheck() throws Exception {
        List<Stage> calls = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        Outcome outcome = new DevelopmentHarness().run(request(1), (stage, input, attempt) -> {
            calls.add(stage);
            if (stage == Stage.EXECUTE) { prompts.add(input); return ok(); }
            return attempt == 1 ? failed() : ok();
        });
        assertEquals(List.of(Stage.EXECUTE, Stage.VERIFY, Stage.EXECUTE, Stage.VERIFY), calls);
        assertEquals(Status.SUCCEEDED, outcome.status());
        assertEquals(2, outcome.attempts());
        assertTrue(prompts.get(1).startsWith(prompts.get(0)));
        assertTrue(prompts.get(1).contains("모든 요청의 결과"));
        assertTrue(prompts.get(1).contains("오류 행도 처리한 결과"));
        assertTrue(prompts.get(1).contains("누락 id: invalid-waiver"));
    }
    @Test void exhaustedBudgetNeverTurnsFailureIntoSuccess() throws Exception {
        for (int repairs : new int[]{0, 1}) {
            List<Stage> calls = new ArrayList<>();
            Outcome outcome = new DevelopmentHarness().run(request(repairs), (stage, input, attempt) -> {
                calls.add(stage);
                return stage == Stage.EXECUTE ? ok() : failed();
            });
            assertEquals(Status.STOPPED, outcome.status());
            assertEquals(Stage.VERIFY, outcome.stage());
            assertEquals(repairs + 1, outcome.attempts());
            assertEquals(2 * (repairs + 1), calls.size());
            assertTrue(outcome.reason().contains("invalid-waiver"));
        }
    }
    @Test void missingTaskOrCheckRunsNothing() throws Exception {
        WorkRequest ready = request(1);
        WorkRequest missingCheck = new WorkRequest(temp, ready.task(), ready.context(), List.of(), 1, 10);
        for (WorkRequest input : List.of(missingCheck, ready)) {
            if (input == ready) Files.writeString(ready.task(), " ");
            Outcome outcome = new DevelopmentHarness().run(input, (stage, prompt, attempt) -> {
                fail("정보가 부족한 요청은 도구를 실행하면 안 됩니다.");
                return ok();
            });
            assertEquals(Status.NEEDS_INPUT, outcome.status());
            assertEquals(Stage.PREPARE, outcome.stage());
            assertEquals(0, outcome.attempts());
        }
    }
    @Test void unavailableOrTimedOutProcessesStopWithoutCodeRepair() throws Exception {
        for (Stage failing : List.of(Stage.EXECUTE, Stage.VERIFY)) {
            for (boolean timeout : new boolean[]{false, true}) {
                List<Stage> calls = new ArrayList<>();
                Outcome outcome = new DevelopmentHarness().run(request(1), (stage, input, attempt) -> {
                    calls.add(stage);
                    if (stage != failing) return ok();
                    if (timeout) return new StepResult(ResultKind.TIMED_OUT, 124, "시간 초과");
                    throw new IOException("프로그램을 시작할 수 없습니다.");
                });
                assertEquals(Status.STOPPED, outcome.status());
                assertEquals(failing, outcome.stage());
                assertEquals(1, outcome.attempts());
                assertEquals(failing == Stage.EXECUTE ? 1 : 2, calls.size());
            }
        }
    }
    @Test void nonzeroWorkExitDoesNotRunVerification() throws Exception {
        List<Stage> calls = new ArrayList<>();
        Outcome outcome = new DevelopmentHarness().run(request(1), (stage, input, attempt) -> {
            calls.add(stage);
            return failed();
        });
        assertEquals(List.of(Stage.EXECUTE), calls);
        assertEquals(Status.STOPPED, outcome.status());
        assertEquals(Stage.EXECUTE, outcome.stage());
    }
    @Test void fileLocationDeterminesWorkspaceAndNumericPolicyIsStrict() throws Exception {
        request(1);
        Path directory = Files.createDirectory(temp.resolve("requests"));
        Path file = directory.resolve("work.json");
        String json = """
            {"workspace":"..","task":"task.md","context":["rules.md"],
             "verify":{"windows":["verify"],"posix":["verify"]},"maxRepairs":1}
            """;
        Files.writeString(file, json);
        WorkRequest loaded = WorkRequest.load(file);
        assertEquals(temp, loaded.workspace());
        assertTrue(loaded.prompt().contains("오류 행도 처리한 결과"));
        for (String invalid : new String[]{"1.5", "\"1\"", "2", "null"}) {
            Files.writeString(file, json.replace("\"maxRepairs\":1", "\"maxRepairs\":" + invalid));
            assertThrows(IllegalArgumentException.class, () -> WorkRequest.load(file));
        }
    }
}
