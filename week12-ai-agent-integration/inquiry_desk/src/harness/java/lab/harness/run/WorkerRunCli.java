package lab.harness.run;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lab.harness.run.WorkerRunManager.Outcome;

/**
 * 실행 진입점. 작업 폴더는 프로젝트 루트이고 설정은 Git에서 제외된 .local/harness/manager.json에서 읽는다.
 * 사용법: start <작업 이름> <요청문 파일> | stop <작업 이름> | status <작업 이름> | release <작업 이름> <시도 번호>
 */
public final class WorkerRunCli {
    /**
     * 로컬 경로와 실행 명령은 장비마다 다르므로 공유하지 않는 설정 파일에 둔다.
     * checkResults는 검사 명령이 실행하는 검사 작업의 이름이다. 비우면 test 하나로 본다.
     */
    public record Config(String repository, String projectPath, String worktreeBase, String baseRef, String ledger,
                         List<String> workerCommand, Map<String, String> workerEnvironment, int workerTimeoutSeconds,
                         List<String> checkCommand, Map<String, String> checkEnvironment, int checkTimeoutSeconds,
                         List<String> checkResults) {}

    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private WorkerRunCli() {}

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path configFile = Path.of(".local/harness/manager.json");
        if (args.length >= 2 && args[0].equals("--config")) {
            configFile = Path.of(args[1]);
            args = Arrays.copyOfRange(args, 2, args.length);
        }
        boolean start = args.length == 3 && args[0].equals("start");
        boolean release = args.length == 3 && args[0].equals("release") && args[2].matches("\\d{1,6}");
        if (!start && !release && !(args.length == 2 && List.of("stop", "status").contains(args[0]))) {
            System.out.println("사용법: [--config <설정 파일>] start <작업 이름> <요청문 파일> | stop <작업 이름>"
                + " | status <작업 이름> | release <작업 이름> <확인한 시도 번호>");
            System.exit(2);
        }
        Config config = JSON.readValue(Files.readString(configFile, StandardCharsets.UTF_8), Config.class);
        TaskLedger ledger = new TaskLedger(Path.of(config.ledger()));
        List<String> results = config.checkResults() == null || config.checkResults().isEmpty() ? List.of("test") : config.checkResults();
        WorkerRunManager manager = new WorkerRunManager(ledger,
            new Workspaces.GitWorktrees(Path.of(config.repository()), Path.of(config.worktreeBase()),
                config.projectPath(), config.baseRef()),
            new ProcessRunner(),
            new GradleChecker(config.checkCommand(), orEmpty(config.checkEnvironment()),
                Duration.ofSeconds(config.checkTimeoutSeconds()), results),
            new WorkerRunManager.Settings(config.workerCommand(), orEmpty(config.workerEnvironment()),
                Duration.ofSeconds(config.workerTimeoutSeconds())));

        String taskId = args[1];
        Outcome outcome;
        if (start) {
            String request = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8);
            // 이 프로그램이 Ctrl+C 등으로 끝날 때 작업자와 검사를 남기지 않도록 중단을 요청하고 정리를 기다린다.
            // 강제로 종료되면 이 정리는 실행되지 않는다. 그때는 잠금과 프로세스 기록이 남아 다음 접수가 거절된다.
            Thread main = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    TaskLedger.Lock lock = ledger.lock(taskId);
                    if (lock != null && lock.manager().pid() == ProcessHandle.current().pid()
                            && ledger.attempt(taskId, lock.attempt()) == null) {
                        Files.writeString(ledger.stopFile(taskId, lock.attempt()), Instant.now().toString());
                        main.join(30_000);
                    }
                } catch (Exception ignored) { /* 종료 중에는 더 할 수 있는 일이 없다. 다음 접수가 남은 프로세스를 확인한다. */ }
            }, "worker-cleanup"));
            outcome = manager.start(taskId, request);
        } else if (args[0].equals("stop")) {
            outcome = manager.stop(taskId, Duration.ofSeconds(30));
        } else if (release) {
            outcome = manager.release(taskId, Integer.parseInt(args[2]));
        } else {
            outcome = manager.current(taskId, "");
        }
        System.out.println(JSON.writeValueAsString(outcome));
        System.exit(switch (outcome.state()) {
            case REVIEW_READY -> 0;
            case QUESTION -> 3;
            case RUNNING -> 4;
            default -> 1;
        });
    }

    private static Map<String, String> orEmpty(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }
}
