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
 * 사용법: start <작업 이름> <요청문 파일> | stop <작업 이름> | status <작업 이름> | release <작업 이름> <시도 번호> | watch <소유자/저장소>
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
        WatchOptions watch = WatchOptions.parse(args);
        if (!start && !release && watch == null && !(args.length == 2 && List.of("stop", "status").contains(args[0]))) {
            System.out.println("사용법: [--config <설정 파일>] start <작업 이름> <요청문 파일> | stop <작업 이름>"
                + " | status <작업 이름> | release <작업 이름> <확인한 시도 번호>"
                + " | watch <소유자/저장소> [--label <라벨>] [--interval <초>]");
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

        if (watch != null) {
            IssueWatch watcher = new IssueWatch(ledger, manager, new GitHubIssues(watch.repository()), watch.label(), System.out);
            Thread main = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> watcher.shutdown(main), "worker-cleanup"));
            watcher.run(watch.interval(), seconds -> Thread.sleep(seconds * 1000L));
            return;
        }

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
                } catch (Exception ignored) { /* 종료 중에는 더 할 수 있는 일이 없다. */ }
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

    record WatchOptions(String repository, String label, int interval) {
        static WatchOptions parse(String[] args) {
            if (args.length < 2 || !args[0].equals("watch") || args.length % 2 != 0
                    || !args[1].matches("[^/\\s]+/[^/\\s]+")) return null;
            String label = "agent-ready";
            int interval = 60;
            var seen = new java.util.HashSet<String>();
            for (int i = 2; i < args.length; i += 2) {
                if (!seen.add(args[i])) return null;
                switch (args[i]) {
                    case "--label" -> label = args[i + 1];
                    case "--interval" -> {
                        try { interval = Integer.parseInt(args[i + 1]); }
                        catch (NumberFormatException invalid) { return null; }
                        if (interval < 1) return null;
                    }
                    default -> { return null; }
                }
            }
            return new WatchOptions(args[1], label, interval);
        }
    }
}
