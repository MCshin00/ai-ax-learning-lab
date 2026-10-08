package lab.harness.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lab.harness.run.TaskLedger.Admission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class TaskLedgerTest {
    @TempDir Path temp;

    static ProcessRunner.Seen vanished() throws Exception {
        Process gone = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        ProcessRunner.Seen seen = ProcessRunner.Seen.of(gone.toHandle());
        gone.waitFor();
        return seen;
    }

    // 한 프로세스 안의 여러 스레드로 확인한다. 서로 다른 실행 관리 프로세스 사이는 운영체제의 파일 잠금이 맡는다.
    @Test void onlyOneOfManySimultaneousAdmissionsInOneProcessRecoversAnAbandonedLock() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
        ProcessRunner.Seen manager = vanished();
        Files.createDirectories(ledger.attemptDir("race", 1));
        Files.writeString(ledger.taskDir("race").resolve("lock"),
            "{\"attempt\":1,\"manager\":{\"pid\":" + manager.pid() + ",\"startedAt\":\"" + manager.startedAt() + "\"}}");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch together = new CountDownLatch(1);
        List<Future<Admission>> admissions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            admissions.add(pool.submit(() -> { together.await(); return ledger.admit("race"); }));
        }
        together.countDown();
        int accepted = 0;
        for (Future<Admission> admission : admissions) {
            if (admission.get(20, TimeUnit.SECONDS).accepted()) accepted++;
        }
        pool.shutdownNow();
        assertEquals(1, accepted);
        assertEquals(2, ledger.attempts("race"));
        assertEquals(2, ledger.lock("race").attempt());
        assertEquals("ABANDONED", ledger.attempt("race", 1).end());
    }

    @Test void finishingAnOlderAttemptDoesNotReleaseANewerLock() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
        assertTrue(ledger.admit("late").accepted());
        ledger.finish("late", new TaskLedger.Attempt(1, "", "", "EXITED", 0, true, true, "PASS", ""),
            TaskLedger.State.REVIEW_READY, "");
        assertEquals(2, ledger.admit("late").attempt());

        // 첫 시도의 끝 기록이 늦게 한 번 더 도착해도 두 번째 시도의 잠금과 상태는 그대로다.
        ledger.finish("late", new TaskLedger.Attempt(1, "", "", "EXITED", 0, true, true, "PASS", ""),
            TaskLedger.State.REVIEW_READY, "");
        assertEquals(2, ledger.lock("late").attempt());
        assertEquals(TaskLedger.State.RUNNING, ledger.task("late").state());
        assertFalse(ledger.admit("late").accepted());
    }

    @Test void aLockWhoseRecordIsBrokenOrIncompleteNeedsAPersonToReleaseIt() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
        // 잠금 파일이 비어 있거나 깨진 경우.
        Files.createDirectories(ledger.taskDir("broken").resolve("attempts"));
        Files.writeString(ledger.taskDir("broken").resolve("lock"), "");
        Admission broken = ledger.admit("broken");
        assertFalse(broken.accepted());
        assertEquals(TaskLedger.State.NEEDS_CHECK, broken.state());
        assertTrue(broken.reason().contains("release"));
        assertNotNull(ledger.release("broken", 1));
        assertNull(ledger.release("broken", 0));
        assertTrue(ledger.admit("broken").accepted());

        // JSON으로는 읽히지만 실행 관리의 식별 정보가 없는 잠금도 읽을 수 없는 잠금이다.
        Files.createDirectories(ledger.taskDir("hollow").resolve("attempts"));
        Files.writeString(ledger.taskDir("hollow").resolve("lock"), "{\"attempt\":1,\"manager\":null}");
        Admission hollow = ledger.admit("hollow");
        assertFalse(hollow.accepted());
        assertTrue(hollow.reason().contains("release"));
        assertThrows(java.io.IOException.class, () -> ledger.requestStop("hollow"));
        // 실행 관리의 번호가 빠진 잠금도 마찬가지다.
        Files.writeString(ledger.taskDir("hollow").resolve("lock"), "{\"attempt\":1,\"manager\":{\"startedAt\":\"\"}}");
        assertThrows(java.io.IOException.class, () -> ledger.lock("hollow"));

        // 작업자를 시작하던 중 실행 관리가 사라져 프로세스 기록이 없는 경우. 프로세스가 없다고 단정하지 않는다.
        ProcessRunner.Seen manager = vanished();
        Files.createDirectories(ledger.attemptDir("launching", 1));
        Files.writeString(ledger.taskDir("launching").resolve("lock"),
            "{\"attempt\":1,\"manager\":{\"pid\":" + manager.pid() + ",\"startedAt\":\"" + manager.startedAt() + "\"}}");
        ledger.markLaunching("launching", 1, "worker");
        Admission launching = ledger.admit("launching");
        assertFalse(launching.accepted());
        assertTrue(launching.reason().contains("기록되지 않은 프로세스"));
        // 작업자의 기록이 있어도 검사를 시작하던 중이었다면 같은 이유로 막는다.
        ledger.recordProcesses("launching", 1, java.util.List.of(manager));
        ledger.clearLaunching("launching", 1, "worker");
        ledger.markLaunching("launching", 1, "check");
        assertFalse(ledger.admit("launching").accepted());
        // 확인한 시도와 지금 잠금의 시도가 다르면 풀지 않는다.
        assertNotNull(ledger.release("launching", 2));
        assertNull(ledger.release("launching", 1));
        assertTrue(ledger.attempt("launching", 1).note().contains("사람이"));
        assertEquals(2, ledger.admit("launching").attempt());

        // 잠금은 온전한데 시도 폴더가 만들어지기 전에 사라진 경우. 띄운 프로세스가 없으므로 접수한다.
        Files.createDirectories(ledger.taskDir("early").resolve("attempts"));
        Files.writeString(ledger.taskDir("early").resolve("lock"),
            "{\"attempt\":1,\"manager\":{\"pid\":" + manager.pid() + ",\"startedAt\":\"" + manager.startedAt() + "\"}}");
        Admission early = ledger.admit("early");
        assertTrue(early.accepted());
        assertEquals("ABANDONED", ledger.attempt("early", 1).end());
    }

    @Test void releaseDoesNotTakeTheLockOfAnUnfinishedAttemptWhoseManagerIsAlive() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
        assertTrue(ledger.admit("live").accepted());
        // 이 검사의 프로세스가 잠금의 실행 관리이고 살아 있으며, 시도의 끝은 아직 기록되지 않았다.
        String refusal = ledger.release("live", 1);
        assertNotNull(refusal);
        assertTrue(refusal.contains("stop"));
        assertEquals(1, ledger.lock("live").attempt());
        assertNotNull(ledger.release("none", 1));
    }

    @Test void aProcessWhoseIdentityCannotBeComparedIsNotTreatedAsGone() {
        // 시작 시각을 적지 못한 기록은 같은 번호의 프로세스가 살아 있으면 가릴 수 없다.
        ProcessRunner.Seen withoutStart = new ProcessRunner.Seen(ProcessHandle.current().pid(), "");
        assertEquals(ProcessRunner.Liveness.UNKNOWN, withoutStart.liveness());
        ProcessRunner.Remaining remaining = ProcessRunner.remaining(java.util.List.of(withoutStart));
        assertTrue(remaining.unknown());
        assertFalse(remaining.none());
        // 가릴 수 없는 프로세스는 종료 대상으로 삼지 않는다.
        assertTrue(remaining.processes().isEmpty());
        // 시작 시각이 다르면 번호가 다시 쓰인 것이다.
        assertEquals(ProcessRunner.Liveness.GONE,
            new ProcessRunner.Seen(ProcessHandle.current().pid(), "2000-01-01T00:00:00Z").liveness());
    }

    @Test void aFailureWhileEndingAProcessIsNotAConfirmedEnd() throws Exception {
        java.util.List<ProcessRunner.Seen> seen = new ArrayList<>();
        ProcessRunner broken = new ProcessRunner((processes, grace) -> { throw new IllegalStateException("종료 실패"); },
            Duration.ofMillis(100));
        ProcessRunner.Result result = broken.run(WorkerRunManagerTest.fake("hang"), temp, Map.of(), "요청",
            temp.resolve("left.log"), Duration.ofSeconds(1), () -> false, seen::addAll);
        try {
            assertEquals(ProcessRunner.End.TIMED_OUT, result.end());
            assertFalse(result.terminationConfirmed());
        } finally {
            ProcessRunner.FORCE.terminate(ProcessHandle.current().descendants().toList(), Duration.ofSeconds(10));
        }
    }

    @Test void aFailureWhileWatchingStillEndsTheProcess() throws Exception {
        List<ProcessRunner.Seen> seen = new ArrayList<>();
        ProcessRunner.Result result = new ProcessRunner().run(
            List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                Path.of(FakeWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                FakeWorker.class.getName(), "hang"),
            temp, Map.of(), "요청", temp.resolve("worker.log"), Duration.ofSeconds(60), () -> false,
            processes -> { seen.addAll(processes); throw new IllegalStateException("기록 실패"); });
        assertEquals(ProcessRunner.End.INTERRUPTED, result.end());
        assertTrue(result.terminationConfirmed());
        assertTrue(result.note().contains("기록 실패"));
        assertFalse(seen.isEmpty());
        assertTrue(seen.stream().allMatch(process -> process.liveness() == ProcessRunner.Liveness.GONE));
    }
}
