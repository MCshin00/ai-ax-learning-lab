package lab.harness.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실행 진입점을 별도 프로세스로 실행해 설정 파일, 실제 worktree, 검사 명령의 연결을 확인한다.
 * 작업자와 검사는 대역이고, 실행 관리 프로세스를 강제로 끝내는 것은 실제로 한다.
 */
class WorkerRunCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temp;
    private final List<ProcessRunner.Seen> started = new ArrayList<>();

    @AfterEach void endLeftovers() {
        List<ProcessHandle> left = new ArrayList<>(ProcessRunner.remaining(started).processes());
        ProcessHandle.current().descendants().forEach(left::add);
        ProcessRunner.FORCE.terminate(left, java.time.Duration.ofSeconds(10));
    }

    private static String location(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    private static String classpath() throws Exception {
        return String.join(System.getProperty("path.separator"), location(WorkerRunCli.class), location(ObjectMapper.class),
            location(com.fasterxml.jackson.core.JsonFactory.class), location(com.fasterxml.jackson.annotation.JsonInclude.class));
    }

    private static void git(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString(),
            "-c", "user.name=test", "-c", "user.email=test@example.invalid"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, process.waitFor(), String.join(" ", arguments));
    }

    private Path config(String name, List<String> worker) throws Exception {
        Path repository = temp.resolve("repo");
        if (!Files.exists(repository)) {
            Files.createDirectories(repository.resolve("app"));
            Files.writeString(repository.resolve("app/file.txt"), "기준");
            git(repository, "init", "-q", "-b", "main");
            git(repository, "add", ".");
            git(repository, "commit", "-q", "-m", "base");
        }
        List<String> check = new ArrayList<>(WorkerRunManagerTest.fake("check", "{build}", "pass", "test"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("repository", repository.toString());
        values.put("projectPath", "app");
        values.put("worktreeBase", temp.resolve("trees").toString());
        values.put("baseRef", "main");
        values.put("ledger", temp.resolve("tasks").toString());
        values.put("workerCommand", worker);
        values.put("workerEnvironment", Map.of());
        values.put("workerTimeoutSeconds", 60);
        values.put("checkCommand", check);
        values.put("checkEnvironment", Map.of());
        values.put("checkTimeoutSeconds", 60);
        values.put("checkResults", List.of("test"));
        Path file = temp.resolve(name + ".json");
        Files.writeString(file, JSON.writeValueAsString(values), StandardCharsets.UTF_8);
        return file;
    }

    private Process cli(Path config, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(WorkerRunManagerTest.java(), "-cp", classpath(),
            WorkerRunCli.class.getName(), "--config", config.toString()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true)
            .redirectOutput(temp.resolve("cli-" + System.nanoTime() + ".log").toFile()).start();
    }

    private int finished(Process process) throws Exception {
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "실행 진입점이 끝나지 않았습니다.");
        return process.exitValue();
    }

    @Test void aRunGoesFromRequestFileToReviewReadyThroughARealWorktree() throws Exception {
        Path request = temp.resolve("request.md");
        Files.writeString(request, "요청문", StandardCharsets.UTF_8);
        Path config = config("done", WorkerRunManagerTest.fake("done", "{result}", "cli"));

        assertEquals(0, finished(cli(config, "start", "feature", request.toString())));
        TaskLedger ledger = new TaskLedger(temp.resolve("tasks"));
        assertEquals(TaskLedger.State.REVIEW_READY, ledger.task("feature").state());
        assertEquals("PASS", ledger.attempt("feature", 1).check());
        assertTrue(Files.readString(ledger.attemptDir("feature", 1).resolve("result.json")).contains("cli:3"));
        assertTrue(Files.exists(ledger.attemptDir("feature", 1).resolve("check/build/test-results/test/TEST-fake.xml")));
        // 작업 폴더는 기준 커밋에서 만든 그 작업만의 폴더다.
        assertEquals("기준", Files.readString(temp.resolve("trees/feature/app/file.txt")));
        assertEquals(0, finished(cli(config, "status", "feature")));
    }

    @Test void afterTheManagerIsKilledTheWorkerBlocksANewRunWhileItIsAlive() throws Exception {
        Path request = temp.resolve("request.md");
        Files.writeString(request, "요청문", StandardCharsets.UTF_8);
        Process manager = cli(config("hang", WorkerRunManagerTest.fake("hang")), "start", "killed", request.toString());
        TaskLedger ledger = new TaskLedger(temp.resolve("tasks"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        // stop이 사용할 작업자 프로세스 기록이 생긴 뒤 실행 관리를 끝낸다.
        while (ledger.attempts("killed") == 0 || ledger.processes("killed", 1).isEmpty()) {
            assertTrue(System.nanoTime() < deadline, "작업자의 프로세스 기록이 생기지 않았습니다.");
            Thread.sleep(50);
        }
        List<ProcessRunner.Seen> workers = ledger.processes("killed", 1);
        started.addAll(workers);

        // 실행 관리만 강제로 끝낸다. 작업자는 남는다.
        manager.destroyForcibly();
        assertTrue(manager.waitFor(20, TimeUnit.SECONDS));
        assertEquals(ProcessRunner.Liveness.SAME, workers.get(0).liveness());
        assertNull(ledger.attempt("killed", 1));

        Path next = config("done", WorkerRunManagerTest.fake("done", "{result}", "cli"));
        assertEquals(1, finished(cli(next, "start", "killed", request.toString())));
        assertEquals(TaskLedger.State.NEEDS_CHECK, ledger.task("killed").state());
        assertEquals(1, ledger.attempts("killed"));

        assertEquals(1, finished(cli(next, "stop", "killed")));
        assertEquals(ProcessRunner.Liveness.GONE, workers.get(0).liveness());
        assertTrue(ledger.attempt("killed", 1).terminationConfirmed());
        assertNotNull(ledger.lock("killed"));
        assertEquals(1, finished(cli(next, "start", "killed", request.toString())));
        assertEquals(1, ledger.attempts("killed"));
        assertEquals(1, finished(cli(next, "release", "killed", "1")));
        assertNull(ledger.lock("killed"));

        assertEquals(0, finished(cli(next, "start", "killed", request.toString())));
        assertEquals(2, ledger.attempts("killed"));
        assertEquals(TaskLedger.State.REVIEW_READY, ledger.task("killed").state());
    }
}
