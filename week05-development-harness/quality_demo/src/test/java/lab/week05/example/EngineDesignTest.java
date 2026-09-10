package lab.week05.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import lab.week05.example.Execution.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineDesignTest {
    @TempDir Path workspace;
    Job job(String goal, List<String> verify) {
        return new Job(goal, workspace, List.of(), List.of("execute"), verify, 1, 10);
    }
    Result ok() { return new Result(Kind.OK, 0, "완료"); }
    Result fail() { return new Result(Kind.FAILED, 1, "누락 id: invalid-waiver"); }
    @Test void bothArchitecturesHonorSameNormalAndRecoveryContract() {
        for (boolean recovery : new boolean[]{false, true}) {
            Result[] replies = recovery ? new Result[]{ok(), fail(), ok(), ok()} : new Result[]{ok(), ok()};
            var stateExecutor = new Scripted(replies); var linearExecutor = new Scripted(replies);
            Job job = job("모든 입력을 처리하세요", List.of("verify"));
            var state = new StateEngine().run(job, stateExecutor);
            var linear = new LinearEngine().run(job, linearExecutor);
            assertEquals("SUCCEEDED", state.state().name()); assertEquals("SUCCEEDED", linear.status());
            assertEquals(linear.attempts(), state.attempts());
            assertEquals(linearExecutor.commands, stateExecutor.commands);
            if (recovery) {
                assertTrue(stateExecutor.inputs.get(2).contains("모든 입력을 처리하세요"));
                assertTrue(stateExecutor.inputs.get(2).contains("누락 id: invalid-waiver"));
                assertTrue(state.transitions().stream().anyMatch(t -> t.event() == StateEngine.Event.REPAIR_ALLOWED));
            }
        }
    }
    @Test void missingAcceptanceInformationNeedsInputBeforeExecution() {
        var executor = new Scripted();
        var state = new StateEngine().run(job("작업", List.of()), executor);
        var linear = new LinearEngine().run(job("작업", List.of()), executor);
        assertEquals(StateEngine.State.NEEDS_INPUT, state.state()); assertEquals("NEEDS_INPUT", linear.status());
        assertTrue(state.reason().contains("검사 명령")); assertEquals(0, executor.commands.size());
    }
    @Test void failureLimitAndExecutionFailureEndWithoutUnsupportedRetry() {
        Job job = job("작업", List.of("verify"));
        var repeated = new Scripted(ok(), fail(), ok(), fail());
        assertEquals(StateEngine.State.STOPPED, new StateEngine().run(job, repeated).state());
        assertEquals(4, repeated.commands.size());
        for (Kind kind : new Kind[]{Kind.TIMED_OUT, Kind.COULD_NOT_START}) {
            var unavailable = new Scripted(new Result(kind, -1, "실행 불가"));
            assertEquals(StateEngine.State.STOPPED, new StateEngine().run(job, unavailable).state());
            assertEquals(1, unavailable.commands.size());
        }
    }
    @Test void verificationCannotSkipExecutionDependency() {
        assertThrows(IllegalStateException.class, () -> StateEngine.next(StateEngine.State.READY,
            StateEngine.Event.VERIFICATION_OK));
    }
    static final class Scripted implements Executor {
        final Queue<Result> results;
        final List<List<String>> commands = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        Scripted(Result... values) { results = new ArrayDeque<>(List.of(values)); }
        public Result run(List<String> command, Path directory, String input, int seconds) {
            commands.add(command); inputs.add(input); return results.remove();
        }
    }
}
