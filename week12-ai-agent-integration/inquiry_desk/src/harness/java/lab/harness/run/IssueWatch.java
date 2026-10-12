package lab.harness.run;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lab.harness.run.WorkerRunManager.Outcome;

final class IssueWatch {
    record Issue(int number, String title, String body) {}
    record Label(String name) {}
    record Detail(int number, String title, String body, String state, List<Label> labels) {}
    interface Issues {
        List<Issue> list(String label) throws IOException, InterruptedException;
        Detail view(int number) throws IOException, InterruptedException;
        void comment(int number, String body) throws IOException, InterruptedException;
    }
    @FunctionalInterface interface Pause { void await(int seconds) throws InterruptedException; }

    private final TaskLedger ledger;
    private final WorkerRunManager manager;
    private final Issues issues;
    private final String label;
    private final PrintStream output;
    private final Set<String> reported = new HashSet<>();
    private volatile String activeTask;
    private volatile boolean stopping;

    IssueWatch(TaskLedger ledger, WorkerRunManager manager, Issues issues, String label, PrintStream output) {
        this.ledger = ledger;
        this.manager = manager;
        this.issues = issues;
        this.label = label;
        this.output = output;
    }

    void run(int interval, Pause pause) throws IOException, InterruptedException {
        while (!stopping) {
            poll();
            if (!stopping) pause.await(interval);
        }
    }

    void poll() throws IOException, InterruptedException {
        List<Issue> listed;
        try { listed = issues.list(label); }
        catch (IOException failure) { lookupFailed(failure); return; }
        for (Issue issue : listed.stream().sorted(Comparator.comparingInt(Issue::number)).toList()) {
            String taskId = "issue-" + issue.number();
            if (Files.exists(ledger.taskDir(taskId).resolve("task.json"))) {
                if (reported.add(taskId)) output.println(taskId + ": 이미 접수한 이슈입니다. 상태 " + ledger.task(taskId).state() + ".");
                continue;
            }
            Detail current;
            try { current = issues.view(issue.number()); }
            catch (IOException failure) { lookupFailed(failure); return; }
            if (!current.state().equals("OPEN") || current.labels().stream().noneMatch(value -> value.name().equals(label))) {
                output.println(taskId + ": 열려 있지 않거나 라벨이 없어 시작하지 않습니다.");
                return;
            }
            String request = preamble().stripTrailing() + "\n\n# " + current.title() + "\n\n" + current.body();
            Outcome outcome;
            activeTask = taskId;
            try {
                if (stopping) return;
                output.println(taskId + ": 작업자를 시작합니다.");
                outcome = manager.start(taskId, request);
            } finally { activeTask = null; }
            output.println(taskId + ": " + outcome.state() + ". " + oneLine(outcome.reason()));
            try { issues.comment(issue.number(), comment(outcome)); }
            catch (IOException failure) {
                output.println(taskId + ": 이슈에 결과를 남기지 못했습니다. " + oneLine(failure.getMessage()));
            }
            return;
        }
        output.println("조회함. 새로 시작할 이슈가 없습니다.");
    }

    static String preamble() throws IOException {
        try (var input = IssueWatch.class.getResourceAsStream("/issue-request-preamble.md")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private String comment(Outcome outcome) throws IOException {
        String name = switch (outcome.state()) {
            case REVIEW_READY -> "검토 대기";
            case CHECK_FAILED -> "검사 실패";
            case CHECK_UNAVAILABLE -> "검사 실행 불가";
            case QUESTION -> "작업자의 질문";
            case NEEDS_CHECK -> "확인 필요";
            case RUNNING -> throw new IllegalStateException("아직 실행 중입니다.");
        };
        return "실행 관리가 이 이슈의 작업을 실행했습니다.\n\n"
            + "- 상태: " + name + " (`" + outcome.state() + "`)\n"
            + "- 브랜치: `task/" + outcome.taskId() + "`\n"
            + "- 시도: " + outcome.attempt()
            + (outcome.state() == TaskLedger.State.REVIEW_READY
                ? "\n- 검사: " + ledger.attempt(outcome.taskId(), outcome.attempt()).checkSummary() : "");
    }

    // start와 같이 중단 파일을 남기고 정리를 기다린다. 접수 직전이라면 잠금이 생길 때까지 기다린다.
    void shutdown(Thread main) {
        stopping = true;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try {
            while (main.isAlive() && activeTask != null && System.nanoTime() < deadline) {
                String taskId = activeTask;
                if (taskId == null) break;
                TaskLedger.Lock lock = ledger.lock(taskId);
                if (lock != null && lock.manager().pid() == ProcessHandle.current().pid()
                        && ledger.attempt(taskId, lock.attempt()) == null) ledger.requestStop(taskId);
                main.join(50);
            }
        } catch (Exception ignored) { /* 종료 중에는 더 할 수 있는 일이 없다. */ }
    }

    private void lookupFailed(IOException failure) {
        output.println("이슈를 조회하지 못했습니다. 다음 조회에서 다시 시도합니다. " + oneLine(failure.getMessage()));
    }

    private static String oneLine(String value) { return value.replaceAll("\\R", " "); }
}
