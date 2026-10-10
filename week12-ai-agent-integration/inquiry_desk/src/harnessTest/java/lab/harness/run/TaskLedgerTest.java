package lab.harness.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    @Test void onlyOneOfManySimultaneousAdmissionsInOneProcessIsAccepted() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
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
        assertEquals(1, ledger.attempts("race"));
        assertEquals(1, ledger.lock("race").attempt());
    }

    @Test void aLockLeftByAVanishedManagerNeedsAPersonToReleaseIt() throws Exception {
        TaskLedger ledger = new TaskLedger(temp);
        ProcessRunner.Seen manager = vanished();
        Files.createDirectories(ledger.attemptDir("vanished", 1));
        Files.writeString(ledger.taskDir("vanished").resolve("lock"),
            "{\"attempt\":1,\"manager\":{\"pid\":" + manager.pid() + ",\"startedAt\":\"" + manager.startedAt() + "\"}}");
        Admission refused = ledger.admit("vanished");
        assertFalse(refused.accepted());
        assertEquals(TaskLedger.State.NEEDS_CHECK, refused.state());
        assertTrue(refused.reason().contains("release"));
        assertNotNull(ledger.release("vanished", 2));
        assertNull(ledger.release("vanished", 1));
        Admission next = ledger.admit("vanished");
        assertTrue(next.accepted());
        assertEquals(2, next.attempt());
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

}
