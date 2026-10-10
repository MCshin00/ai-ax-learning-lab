package lab.harness.run;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.harness.run.Checker.Check;
import lab.harness.run.TaskLedger.State;
import lab.harness.run.WorkerRunManager.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** 실행 관리의 정책을 대역 프로세스로 확인한다. 실제 코딩 작업자와 Gradle은 실행하지 않는다. */
class WorkerRunManagerTest {
    private static final Checker PASS = (project, directory, stop, watcher) -> new Check(Check.Verdict.PASS, "통과");

    @TempDir Path temp;
    private final List<ProcessRunner.Seen> started = new ArrayList<>();

    @AfterEach void endLeftovers() {
        // 끝날 때까지 기다린다. 로그 파일을 쥔 프로세스가 남아 있으면 임시 폴더를 지울 수 없다.
        List<ProcessHandle> left = new ArrayList<>(ProcessRunner.remaining(started).processes());
        ProcessHandle.current().descendants().forEach(left::add);
        ProcessRunner.FORCE.terminate(left, Duration.ofSeconds(10));
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    // Gradle의 검사 JVM은 자기 클래스패스를 따로 관리하므로 대역 클래스가 있는 폴더를 직접 넘긴다.
    static String classes() {
        try { return Path.of(FakeWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    static List<String> fake(String... arguments) {
        List<String> command = new ArrayList<>(List.of(java(), "-cp", classes(), FakeWorker.class.getName()));
        command.addAll(List.of(arguments));
        return command;
    }

    private TaskLedger ledger() {
        return new TaskLedger(temp.resolve("tasks"));
    }

    private WorkerRunManager manager(ProcessRunner runner, Checker checker, int timeoutSeconds, String... worker) {
        Workspaces workspaces = taskId -> Files.createDirectories(temp.resolve("work").resolve(taskId));
        return new WorkerRunManager(ledger(), workspaces, runner, checker,
            new WorkerRunManager.Settings(fake(worker), Map.of(), Duration.ofSeconds(timeoutSeconds)));
    }

    private WorkerRunManager manager(Checker checker, int timeoutSeconds, String... worker) {
        return manager(new ProcessRunner(), checker, timeoutSeconds, worker);
    }

    private CompletableFuture<Outcome> inBackground(WorkerRunManager manager, String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try { return manager.start(taskId, "요청"); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private List<ProcessRunner.Seen> recorded(String taskId, int attempt, int atLeast) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            List<ProcessRunner.Seen> processes = ledger().processes(taskId, attempt);
            if (processes.size() >= atLeast) {
                started.addAll(processes);
                return processes;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("프로세스 기록이 생기지 않았습니다.");
    }

    private static boolean noneAlive(List<ProcessRunner.Seen> processes) {
        return processes.stream().allMatch(seen -> seen.liveness() == ProcessRunner.Liveness.GONE);
    }

    @Test void timeoutEndsTheProcessAndConfirmsItIsGone() throws Exception {
        Outcome outcome = manager(PASS, 1, "hang").start("slow", "요청");
        var attempt = ledger().attempt("slow", 1);
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertEquals("TIMED_OUT", attempt.end());
        assertTrue(attempt.terminationConfirmed());
        assertTrue(noneAlive(ledger().processes("slow", 1)));
        assertNull(ledger().lock("slow"));
    }

    @Test void stopRequestEndsChildProcessesToo() throws Exception {
        Path childPid = temp.resolve("child.pid");
        WorkerRunManager manager = manager(PASS, 60, "family", childPid.toString());
        CompletableFuture<Outcome> running = inBackground(manager, "family");
        // 대역이 하위 프로세스의 번호를 파일에 남기고, 실행 관리가 그 번호를 기록할 때까지 기다린다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(childPid) || Files.readString(childPid).isBlank()) {
            assertTrue(System.nanoTime() < deadline, "대역이 하위 프로세스를 띄우지 않았습니다.");
            Thread.sleep(50);
        }
        long child = Long.parseLong(Files.readString(childPid));
        List<ProcessRunner.Seen> processes = recorded("family", 1, 2);
        while (processes.stream().noneMatch(seen -> seen.pid() == child)) {
            assertTrue(System.nanoTime() < deadline, "하위 프로세스가 기록되지 않았습니다.");
            Thread.sleep(50);
            processes = recorded("family", 1, 2);
        }

        manager.stop("family", Duration.ofSeconds(20));
        Outcome outcome = running.get(20, TimeUnit.SECONDS);
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertEquals("INTERRUPTED", ledger().attempt("family", 1).end());
        assertTrue(ledger().attempt("family", 1).terminationConfirmed());
        assertTrue(noneAlive(processes));
    }

    @Test void anObservedChildLeftBehindByAWrapperThatExitedNormallyIsEnded() throws Exception {
        // 감싸는 명령은 정상 종료하고 그 하위 프로세스만 남는 경우. 감싸는 명령이 살아 있는 동안 찾은 하위 프로세스를 끝낸다.
        Path childPid = temp.resolve("wrapped.pid");
        Outcome outcome = manager(PASS, 60, "wrapper", childPid.toString(), "1500").start("wrapped", "요청");
        long child = Long.parseLong(Files.readString(childPid));
        List<ProcessRunner.Seen> processes = ledger().processes("wrapped", 1);
        started.addAll(processes);
        assertTrue(processes.stream().anyMatch(seen -> seen.pid() == child));
        assertTrue(ProcessHandle.of(child).filter(ProcessHandle::isAlive).isEmpty());
        assertEquals("EXITED", ledger().attempt("wrapped", 1).end());
        assertTrue(ledger().attempt("wrapped", 1).terminationConfirmed());
        assertTrue(ledger().attempt("wrapped", 1).note().contains("남아 있던 하위 프로세스"));
        // 결과 파일을 남기지 않았으므로 검토 대기가 아니다.
        assertEquals(State.NEEDS_CHECK, outcome.state());
    }

    @Test void unconfirmedTerminationBlocksTheNextRunUntilItIsConfirmed() throws Exception {
        // 종료 요청이 먹히지 않는 상황을 만든다. 프로세스는 실제로 남는다.
        ProcessRunner unableToEnd = new ProcessRunner((processes, grace) -> false, Duration.ofMillis(100));
        Outcome first = manager(unableToEnd, PASS, 1, "hang").start("stuck", "요청");
        started.addAll(ledger().processes("stuck", 1));
        assertEquals(State.NEEDS_CHECK, first.state());
        assertFalse(ledger().attempt("stuck", 1).terminationConfirmed());
        assertTrue(first.reason().contains("종료가 확인되지 않았습니다"));

        WorkerRunManager next = manager(PASS, 60, "done", "{result}", "retry");
        Outcome refused = next.start("stuck", "다시");
        assertFalse(refused.accepted());
        assertEquals(State.NEEDS_CHECK, refused.state());
        assertEquals(1, ledger().attempts("stuck"));
        assertEquals(State.NEEDS_CHECK, ledger().task("stuck").state());

        next.stop("stuck", Duration.ofSeconds(20));
        assertTrue(ledger().attempt("stuck", 1).terminationConfirmed());
        assertTrue(noneAlive(ledger().processes("stuck", 1)));
        assertNotNull(ledger().lock("stuck"));
        assertFalse(next.start("stuck", "다시").accepted());
        next.release("stuck", 1);
        Outcome retried = next.start("stuck", "다시");
        assertTrue(retried.accepted());
        assertEquals(2, retried.attempt());
        assertEquals(State.REVIEW_READY, retried.state());
    }

    @Test void eachAttemptKeepsItsOwnRequestResultLogAndCheck() throws Exception {
        Checker leavesAFile = (project, directory, stop, watcher) -> {
            try { Files.writeString(Files.createDirectories(directory).resolve("gradle.log"), "검사 " + directory.getParent().getFileName()); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            return new Check(Check.Verdict.PASS, "통과");
        };
        assertEquals(State.REVIEW_READY, manager(leavesAFile, 60, "done", "{result}", "first").start("twice", "하나").state());
        assertEquals(State.REVIEW_READY, manager(leavesAFile, 60, "done", "{result}", "second").start("twice", "둘둘").state());
        Path attempts = ledger().taskDir("twice").resolve("attempts");
        assertEquals("하나", Files.readString(attempts.resolve("1/request.md"), StandardCharsets.UTF_8));
        assertEquals("둘둘", Files.readString(attempts.resolve("2/request.md"), StandardCharsets.UTF_8));
        // 대역은 표준 입력이 닫혀야 결과를 쓴다. 결과의 길이는 전달된 요청의 길이다.
        assertTrue(Files.readString(attempts.resolve("1/result.json")).contains("first:2"));
        assertTrue(Files.readString(attempts.resolve("2/result.json")).contains("second:2"));
        for (String attempt : List.of("1", "2")) {
            assertTrue(Files.exists(attempts.resolve(attempt + "/worker.log")));
            assertTrue(Files.exists(attempts.resolve(attempt + "/processes.json")));
            assertEquals("검사 " + attempt, Files.readString(attempts.resolve(attempt + "/check/gradle.log")));
        }
        assertEquals(2, ledger().task("twice").attempts());
    }

    @Test void failedCheckAndUnavailableCheckAreRecordedAsDifferentStates() throws Exception {
        Checker failed = (project, directory, stop, watcher) -> new Check(Check.Verdict.FAILED, "검사 1, 실패 1");
        Checker unavailable = (project, directory, stop, watcher) -> new Check(Check.Verdict.UNAVAILABLE, "결과 파일이 없습니다");
        Outcome a = manager(failed, 60, "done", "{result}", "a").start("check-failed", "요청");
        Outcome b = manager(unavailable, 60, "done", "{result}", "b").start("check-unavailable", "요청");
        assertEquals(State.CHECK_FAILED, a.state());
        assertEquals(State.CHECK_UNAVAILABLE, b.state());
        assertEquals("FAILED", ledger().attempt("check-failed", 1).check());
        assertEquals("UNAVAILABLE", ledger().attempt("check-unavailable", 1).check());
        assertEquals("검사 1, 실패 1", ledger().task("check-failed").reason());
    }

    @Test void aCheckWhoseProcessesMayRemainKeepsTheLock() throws Exception {
        Checker leftRunning = (project, directory, stop, watcher) ->
            new Check(Check.Verdict.UNAVAILABLE, "검사 프로세스의 종료가 확인되지 않았습니다.", false);
        Outcome outcome = manager(leftRunning, 60, "done", "{result}", "x").start("check-left", "요청");
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertFalse(ledger().attempt("check-left", 1).terminationConfirmed());
        assertNotNull(ledger().lock("check-left"));
    }

    @Test void aStopRequestReachesTheFinalCheck() throws Exception {
        // 검사 대역으로 끝나지 않는 프로세스를 실행한다. 작업자는 이미 끝났고 검사만 실행 중일 때 중단을 요청한다.
        Checker hanging = (project, directory, stop, watcher) -> {
            ProcessRunner.Result result = new ProcessRunner().run(fake("hang"), project, Map.of(), "",
                directory.resolve("gradle.log"), Duration.ofSeconds(60), stop, watcher);
            return GradleChecker.assess(result, directory.resolve("build/test-results"), List.of("test"));
        };
        WorkerRunManager manager = manager(hanging, 60, "done", "{result}", "x");
        CompletableFuture<Outcome> running = inBackground(manager, "check-stop");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!Files.exists(ledger().attemptDir("check-stop", 1).resolve("check/gradle.log")) && System.nanoTime() < deadline) Thread.sleep(50);
        manager.stop("check-stop", Duration.ofSeconds(20));
        Outcome outcome = running.get(20, TimeUnit.SECONDS);
        started.addAll(ledger().processes("check-stop", 1));
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertTrue(outcome.reason().contains("중단 요청"));
        assertTrue(ledger().attempt("check-stop", 1).terminationConfirmed());
        // 작업자와 검사의 프로세스가 같은 기록에 있고 모두 사라졌다.
        assertTrue(ledger().processes("check-stop", 1).size() >= 2);
        assertTrue(noneAlive(ledger().processes("check-stop", 1)));
    }

    @Test void aStoppedWorkerIsAQuestionAndIsNotChecked() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Checker counting = (project, directory, stop, watcher) -> { checks.incrementAndGet(); return new Check(Check.Verdict.PASS, ""); };
        assertEquals(State.QUESTION, manager(counting, 60, "stopped", "{result}", "q").start("question", "요청").state());
        assertEquals(State.NEEDS_CHECK, manager(counting, 60, "silent").start("no-result", "요청").state());
        // 질문을 남기고 멈춘 결과가 있어도 비정상 종료였으면 질문 대기가 아니라 확인 필요다.
        Outcome both = manager(counting, 60, "stopped-fail", "{result}", "q").start("question-and-exit-code", "요청");
        assertEquals(State.NEEDS_CHECK, both.state());
        assertTrue(both.reason().contains("종료 코드 7"));
        assertTrue(Files.exists(ledger().attemptDir("question-and-exit-code", 1).resolve("result.json")));
        assertEquals(0, checks.get());
    }

    @Test void aResultMissingThePromisedItemsIsNotReadyForReview() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Checker counting = (project, directory, stop, watcher) -> { checks.incrementAndGet(); return new Check(Check.Verdict.PASS, ""); };
        Outcome outcome = manager(counting, 60, "partial", "{result}").start("partial", "요청");
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertEquals("멈췄는데 질문이 없습니다.", outcome.reason());
        assertEquals(0, checks.get());
    }

    @Test void aStopRequestedWhileTheWorkspaceIsPreparedNeverStartsTheWorker() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Checker counting = (project, directory, stop, watcher) -> { checks.incrementAndGet(); return new Check(Check.Verdict.PASS, ""); };
        Workspaces requestsStop = taskId -> {
            Files.writeString(ledger().stopFile(taskId, 1), "");
            return Files.createDirectories(temp.resolve("work").resolve(taskId));
        };
        WorkerRunManager manager = new WorkerRunManager(ledger(), requestsStop, new ProcessRunner(), counting,
            new WorkerRunManager.Settings(fake("done", "{result}", "x"), Map.of(), Duration.ofSeconds(60)));
        Outcome outcome = manager.start("stop-early", "요청");
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertEquals("중단 요청이 있어 작업자를 시작하지 않았습니다.", outcome.reason());
        assertEquals("NOT_STARTED", ledger().attempt("stop-early", 1).end());
        assertFalse(Files.exists(ledger().attemptDir("stop-early", 1).resolve("worker.log")));
        assertNull(ledger().lock("stop-early"));
        assertEquals(0, checks.get());
    }

    @Test void aStopRequestThatArrivesAsTheWorkerEndsIsStillAStop() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Checker counting = (project, directory, stop, watcher) -> { checks.incrementAndGet(); return new Check(Check.Verdict.PASS, ""); };
        // 대역이 끝났다는 결과를 쓰고 끝나기 직전에 중단 요청 파일을 만든다. 실행 관리가 정상 종료로 보았든 중단으로 끝냈든
        // 결과를 읽어 검토 대기로 올리지 않고 최종 검사도 실행하지 않는다. 두 경로 가운데 어느 쪽을 지났는지는 이 검사가 가리지 않는다.
        String stopFile = ledger().attemptDir("stop-at-end", 1).resolve("stop-requested").toString();
        Outcome outcome = manager(counting, 60, "done-then-stop", "{result}", "x", stopFile).start("stop-at-end", "요청");
        assertEquals(State.NEEDS_CHECK, outcome.state());
        assertTrue(outcome.reason().contains("중단 요청"), outcome.reason());
        assertEquals(0, checks.get());
    }

    @Test void aSessionStoppedByTheStopHookFollowsTheFinalCheckAndKeepsTheFact() throws Exception {
        // 종료 Hook이 세션을 멈췄어도 상태는 최종 검사의 판정을 따른다. 멈췄다는 사실은 이유에 남는다.
        Outcome passed = manager(PASS, 60, "hooked", "{result}", "a", "stopped").start("hook-pass", "요청");
        assertEquals(State.REVIEW_READY, passed.state());
        assertTrue(passed.reason().contains("종료 Hook이 작업자의 세션을 멈췄습니다: 대역 Hook 판정"));
        // Hook의 기록은 그 시도의 폴더에 남는다.
        assertTrue(Files.readString(ledger().attemptDir("hook-pass", 1).resolve("hook-event.json")).contains("\"hook_action\":\"stopped\""));

        // Hook이 멈추지 않은 실행의 이유에는 붙지 않는다.
        Outcome fixed = manager(PASS, 60, "hooked", "{result}", "c", "fix_once").start("hook-fixed", "요청");
        assertEquals(State.REVIEW_READY, fixed.state());
        assertFalse(fixed.reason().contains("종료 Hook"));

        // 같은 작업의 다음 시도에는 앞선 시도의 Hook 기록이 넘어오지 않는다.
        Outcome next = manager(PASS, 60, "done", "{result}", "d").start("hook-pass", "요청");
        assertEquals(State.REVIEW_READY, next.state());
        assertFalse(next.reason().contains("종료 Hook"));
        assertFalse(Files.exists(ledger().attemptDir("hook-pass", 2).resolve("hook-event.json")));
    }

}
