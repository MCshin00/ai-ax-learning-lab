package lab.harness.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lab.harness.run.Checker.Check;
import lab.harness.run.TaskLedger.Admission;
import lab.harness.run.TaskLedger.Attempt;
import lab.harness.run.TaskLedger.State;

/**
 * 작업 한 건의 실행 시도 하나를 접수부터 최종 검사까지 잇는다.
 * 접수 → 작업 폴더 준비 → 작업자 실행 → 종료 확인 → 결과 읽기 → 최종 검사 → 상태 기록.
 * 검사가 실패해도 작업자를 다시 실행하지 않는다. 수정 요청은 메인이 새 시도로 넘긴다.
 */
public final class WorkerRunManager {
    /** workerCommand의 {workspace}는 작업 폴더로, {result}는 작업자가 결과를 쓸 파일로 바뀐다. */
    public record Settings(List<String> workerCommand, Map<String, String> workerEnvironment, Duration workerTimeout) {}

    public record Outcome(boolean accepted, String taskId, int attempt, State state, String reason, String attemptDir) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TaskLedger ledger;
    private final Workspaces workspaces;
    private final ProcessRunner runner;
    private final Checker checker;
    private final Settings settings;

    public WorkerRunManager(TaskLedger ledger, Workspaces workspaces, ProcessRunner runner, Checker checker, Settings settings) {
        this.ledger = ledger;
        this.workspaces = workspaces;
        this.runner = runner;
        this.checker = checker;
        this.settings = settings;
    }

    public Outcome start(String taskId, String request) throws IOException {
        Admission admission = ledger.admit(taskId);
        if (!admission.accepted()) {
            return new Outcome(false, taskId, admission.attempt(), admission.state(), admission.reason(), "");
        }
        int number = admission.attempt();
        Path directory = ledger.attemptDir(taskId, number);
        String started = Instant.now().toString();
        // 작업자와 검사가 띄운 프로세스를 한 목록에 모아 기록한다. 실행 관리가 사라져도 다음 접수가 이 목록을 본다.
        // 번호와 시작 시각을 함께 식별자로 쓴다. 번호가 다시 쓰여도 새 프로세스의 기록을 버리지 않는다.
        Set<ProcessRunner.Seen> launched = new LinkedHashSet<>();
        String[] phase = {"worker"};
        ProcessRunner.Watcher watcher = processes -> {
            synchronized (launched) {
                launched.addAll(processes);
                try {
                    ledger.recordProcesses(taskId, number, List.copyOf(launched));
                } catch (RuntimeException unrecorded) {
                    // 기록에 없는 프로세스가 생겼다. 표시를 다시 세워, 종료가 확인되지 않으면 다음 접수가 옛 기록만 보고 잠금을 풀지 않게 한다.
                    try { ledger.markLaunching(taskId, number, phase[0]); }
                    catch (IOException alsoFailed) { unrecorded.addSuppressed(alsoFailed); }
                    throw unrecorded;
                }
                // 지금까지 본 프로세스가 모두 기록됐으므로 표시를 지운다.
                ledger.clearLaunching(taskId, number, phase[0]);
            }
        };
        Path stopFile = ledger.stopFile(taskId, number);
        ProcessRunner.StopSignal stop = () -> Files.exists(stopFile);

        ProcessRunner.Result run = null;
        // 프로세스를 시작하려는 동안에는 끝났다고 볼 근거가 없다. 결과를 받은 뒤에만 확인으로 바꾼다.
        boolean confirmed = true;
        State state;
        String reason;
        String check = "";
        String hookNote = "";
        try {
            Files.writeString(directory.resolve("request.md"), request, StandardCharsets.UTF_8);
            // 작업 폴더를 만드는 git도 이 시도가 시작하는 프로세스다. 시작하기 전에 표시를 남기고, 끝난 것이 확인된 뒤에만 지운다.
            // 그 사이에 실행 관리가 사라지거나 git의 종료를 확인하지 못하면 표시가 남아 다음 접수가 잠금을 풀지 못한다.
            confirmed = false;
            ledger.markLaunching(taskId, number, "workspace");
            Path workspace;
            try {
                workspace = workspaces.prepare(taskId);
            } catch (Workspaces.StillRunning left) {
                throw left;
            } catch (Exception finished) {
                ledger.clearLaunching(taskId, number, "workspace");
                confirmed = true;
                throw finished;
            }
            ledger.clearLaunching(taskId, number, "workspace");
            confirmed = true;
            ledger.recordWorkspace(taskId, workspace);
            Path resultFile = directory.resolve("result.json");
            List<String> command = settings.workerCommand().stream().map(part -> part
                .replace("{workspace}", workspace.toString()).replace("{result}", resultFile.toString())).toList();
            // 종료 Hook이 이번 실행에서 남긴 판정만 가져오도록 앞선 실행의 기록을 지운다.
            Path hookRecord = workspace.resolve(".local/harness/last-hook-event.json");
            Files.deleteIfExists(hookRecord);
            // 작업 폴더를 준비하는 동안 중단 요청이 들어왔으면 작업자를 시작하지 않는다.
            if (stop.requested()) throw new StopRequested("중단 요청이 있어 작업자를 시작하지 않았습니다.");
            ledger.markLaunching(taskId, number, "worker");
            confirmed = false;
            run = runner.run(command, workspace, settings.workerEnvironment(), request, directory.resolve("worker.log"),
                settings.workerTimeout(), stop, watcher);
            confirmed = run.terminationConfirmed();
            // Hook이 걸렸는지와 그 판정을 시도의 기록으로 남긴다. 파일이 없으면 Hook이 실행되지 않았거나 판정까지 가지 못한 것이다.
            if (Files.isRegularFile(hookRecord)) {
                Files.copy(hookRecord, directory.resolve("hook-event.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                hookNote = hookStopped(hookRecord);
            }
            // 시작하지 못했거나 종료가 확인된 경우에만 시작 표시를 지운다. 번호가 기록됐다면 watcher가 이미 지웠다.
            // 프로세스를 기록하지 못했고 종료도 확인되지 않았으면 표시가 남아, 기록에 없는 프로세스가 있을 수 있음을 알린다.
            if (run.end() == ProcessRunner.End.START_FAILED || run.terminationConfirmed()) {
                ledger.clearLaunching(taskId, number, "worker");
            }

            // 실행이 정상으로 끝나지 않았으면 작업자가 남긴 결과와 관계없이 사람이 확인한다. 결과 파일은 시도 폴더에 그대로 남는다.
            if (!run.terminationConfirmed()) {
                state = State.NEEDS_CHECK;
                reason = "작업자의 종료가 확인되지 않았습니다. " + run.note();
            } else if (run.end() == ProcessRunner.End.START_FAILED) {
                state = State.NEEDS_CHECK;
                reason = "작업자를 시작하지 못했습니다. " + run.note();
            } else if (run.end() == ProcessRunner.End.TIMED_OUT) {
                state = State.NEEDS_CHECK;
                reason = "작업자가 제한 시간 " + settings.workerTimeout().toSeconds() + "초 안에 끝나지 않아 종료했습니다.";
            } else if (run.end() == ProcessRunner.End.INTERRUPTED) {
                state = State.NEEDS_CHECK;
                reason = run.note().isEmpty() ? "중단 요청으로 작업자를 종료했습니다." : run.note();
            } else if (stop.requested()) {
                // 작업자가 끝나는 순간에 들어온 중단 요청도 중단이다. 결과를 읽거나 최종 검사로 넘어가지 않는다.
                state = State.NEEDS_CHECK;
                reason = "중단 요청이 있어 작업자의 결과를 읽지 않았고 최종 검사를 실행하지 않았습니다.";
            } else if (run.exitCode() != 0) {
                state = State.NEEDS_CHECK;
                reason = "작업자가 종료 코드 " + run.exitCode() + "로 끝났습니다.";
            } else if (!run.inputDelivered()) {
                state = State.NEEDS_CHECK;
                reason = "작업자는 정상 종료했지만 요청을 끝까지 전달하지 못했습니다.";
            } else {
                String problem = WorkerResult.problem(resultFile);
                if (problem != null) {
                    state = State.NEEDS_CHECK;
                    reason = "작업자의 결과가 약속한 형식이 아닙니다: " + problem;
                } else if (WorkerResult.stopped(resultFile)) {
                    state = State.QUESTION;
                    reason = "작업자가 질문을 남기고 멈췄습니다.";
                } else {
                    phase[0] = "check";
                    ledger.markLaunching(taskId, number, "check");
                    confirmed = false;
                    Check verdict = checker.run(workspace, directory.resolve("check"), stop, watcher);
                    confirmed = verdict.terminationConfirmed();
                    if (verdict.terminationConfirmed()) ledger.clearLaunching(taskId, number, "check");
                    check = verdict.verdict().name();
                    reason = verdict.summary();
                    if (!verdict.terminationConfirmed()) {
                        state = State.NEEDS_CHECK;
                    } else if (stop.requested()) {
                        state = State.NEEDS_CHECK;
                        reason = "중단 요청으로 최종 검사를 끝냈습니다. " + verdict.summary();
                    } else {
                        state = switch (verdict.verdict()) {
                            case PASS -> State.REVIEW_READY;
                            case FAILED -> State.CHECK_FAILED;
                            case UNAVAILABLE -> State.CHECK_UNAVAILABLE;
                        };
                    }
                }
            }
        } catch (Exception failure) {
            // 프로세스를 시작하려던 중의 오류라면 confirmed가 false로 남아 잠금이 풀리지 않는다.
            state = State.NEEDS_CHECK;
            reason = (failure instanceof StopRequested ? "" : failure.getClass().getSimpleName() + ": ") + failure.getMessage()
                + (confirmed ? "" : " 시작한 프로세스의 종료가 확인되지 않았습니다.");
        }
        // 종료 Hook은 작업자가 스스로 하는 검사다. 상태는 위에서 정한 대로 두고, Hook이 세션을 멈췄다는 사실을 이유에 덧붙인다.
        reason = (reason + hookNote).strip();
        ledger.finish(taskId, new Attempt(number, started, Instant.now().toString(),
            run == null ? "NOT_STARTED" : run.end().name(), run == null ? -1 : run.exitCode(),
            run != null && run.inputDelivered(), confirmed, check, run == null ? "" : run.note()), state, reason);
        return new Outcome(true, taskId, number, state, reason, directory.toString());
    }

    /**
     * 실행 중인 시도 전체(작업자와 최종 검사)에 중단을 요청한다. 그 시도를 지켜보던 실행 관리가 이미 사라졌다면
     * 기록된 프로세스와 그 하위 프로세스에 직접 종료를 요청하고 사라졌는지 확인한다.
     */
    public Outcome stop(String taskId, Duration wait) throws IOException {
        TaskLedger.Lock lock;
        try { lock = ledger.requestStop(taskId); }
        catch (IOException | RuntimeException unreadable) {
            return new Outcome(false, taskId, 0, State.NEEDS_CHECK, TaskLedger.UNREADABLE_LOCK, "");
        }
        if (lock == null) return current(taskId, "실행 중인 시도가 없습니다.");
        int number = lock.attempt();
        if (ledger.attempt(taskId, number) == null && lock.manager().liveness() != ProcessRunner.Liveness.GONE) {
            long deadline = System.nanoTime() + wait.toNanos();
            while (ledger.attempt(taskId, number) == null && System.nanoTime() < deadline) pause();
            return current(taskId, "중단을 요청했지만 시도가 아직 끝나지 않았습니다.");
        }
        // 실행 관리가 사라졌다. 시작과 기록 사이에 사라졌다면 기록에 없는 프로세스가 있을 수 있어 끝났다고 할 수 없다.
        if (ledger.launchPending(taskId, number)) {
            ledger.needsCheck(taskId, ledger.unrecordedLaunch(taskId, number));
            return current(taskId, "");
        }
        // 찾고, 기록하고, 종료를 확인하는 동안 다른 접수가 옛 기록만 보고 잠금을 풀지 않도록 표시를 먼저 남긴다.
        ledger.markLaunching(taskId, number, "stop");
        List<ProcessRunner.Seen> recorded = ledger.processes(taskId, number);
        ProcessRunner.Remaining remaining = ProcessRunner.remaining(recorded);
        // 기록에 없던 하위 프로세스를 찾았으면 종료를 요청하기 전에 기록한다. 종료에 실패해도 다음 접수가 그것을 본다.
        Set<ProcessRunner.Seen> all = new LinkedHashSet<>(recorded);
        remaining.processes().forEach(handle -> all.add(ProcessRunner.Seen.of(handle)));
        boolean allRecorded = true;
        if (all.size() != recorded.size()) {
            try { ledger.recordProcesses(taskId, number, List.copyOf(all)); }
            catch (RuntimeException unrecorded) { allRecorded = false; }
        }
        boolean gone = remaining.processes().isEmpty() || ProcessRunner.FORCE.terminate(remaining.processes(), wait);
        gone = gone && ProcessRunner.remaining(List.copyOf(all)).none();
        // 모두 사라졌거나, 남았더라도 전부 기록돼 있으면 표시를 지운다. 기록하지 못한 것이 남았으면 표시를 둔다.
        if (gone || allRecorded) ledger.clearLaunching(taskId, number, "stop");
        Attempt before = ledger.attempt(taskId, number);
        String note = gone ? "남아 있던 프로세스의 종료를 확인했습니다."
            : "종료를 요청했지만 프로세스가 남아 있거나 같은 프로세스인지 가릴 수 없습니다.";
        ledger.finish(taskId, new Attempt(number, before == null ? "" : before.startedAt(), Instant.now().toString(),
            before == null ? "ABANDONED" : before.end(), before == null ? -1 : before.exitCode(),
            before != null && before.inputDelivered(), gone, before == null ? "" : before.check(), note),
            State.NEEDS_CHECK, note);
        return current(taskId, "");
    }

    /**
     * 프로그램이 종료를 확인할 수 없을 때, 사람이 남은 프로세스가 없음을 확인하고 잠금을 푼다.
     * attempt는 확인한 시도의 번호이고 읽을 수 없는 잠금은 0이다.
     */
    public Outcome release(String taskId, int attempt) throws IOException {
        String refused = ledger.release(taskId, attempt);
        Outcome current = current(taskId, "");
        return refused == null ? current
            : new Outcome(false, taskId, current.attempt(), current.state(), "잠금을 풀지 않았습니다. " + refused, current.attemptDir());
    }

    public Outcome current(String taskId, String fallback) throws IOException {
        TaskLedger.Task task = ledger.task(taskId);
        if (task == null) return new Outcome(false, taskId, 0, State.NEEDS_CHECK, "접수된 적이 없는 작업입니다.", "");
        return new Outcome(false, taskId, task.attempts(), task.state(), task.reason().isEmpty() ? fallback : task.reason(),
            task.attempts() == 0 ? "" : ledger.attemptDir(taskId, task.attempts()).toString());
    }

    private static final class StopRequested extends Exception {
        StopRequested(String message) { super(message); }
    }

    // 종료 Hook이 남긴 기록에서, Hook이 세션을 멈췄으면 이유에 덧붙일 문장을 돌려준다. 멈추지 않았거나 읽을 수 없으면 빈 문자열.
    private static String hookStopped(Path record) {
        try {
            JsonNode event = JSON.readTree(Files.readString(record, StandardCharsets.UTF_8));
            if (!"stopped".equals(event.path("hook_action").asText())) return "";
            return " 종료 Hook이 작업자의 세션을 멈췄습니다: " + event.path("summary").asText();
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    /**
     * 작업자의 결과 파일. 항목의 이름과 타입은 src/harness/resources/worker-result.schema.json에 선언돼 있다.
     * 작업자가 그 형식으로 답하게 하는 것은 설정의 작업자 명령과 요청문이 맡고, 실행 관리는 이 파일을 읽지 않는다.
     * 여기서는 돌아온 결과가 그 형식인지와 스키마로 적지 못한 조건을 확인한다. 문자열 항목이 비어 있지 않은지, 멈춘 결과에 질문이 있는지,
     * 끝난 결과에 검사 기록과 본문 초안이 있는지다. 스키마에 없는 항목이 더 있어도 거절하지 않는다.
     */
    static final class WorkerResult {
        private WorkerResult() {}

        /** 약속한 형식이면 null, 아니면 어긋난 곳. */
        static String problem(Path file) {
            JsonNode result;
            try { result = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)); }
            catch (IOException | RuntimeException unreadable) { return "결과 파일을 읽을 수 없습니다."; }
            if (result == null || !result.isObject()) return "결과가 JSON 객체가 아닙니다.";
            String status = result.path("status").asText("");
            if (!List.of("done", "stopped").contains(status)) return "status가 done 또는 stopped가 아닙니다.";
            for (String name : List.of("summary", "pr_body")) {
                if (!result.path(name).isTextual()) return name + "이(가) 문자열이 아닙니다.";
            }
            for (String name : List.of("questions", "changed_files", "checks", "unchecked")) {
                if (!result.path(name).isArray()) return name + "이(가) 목록이 아닙니다.";
            }
            for (JsonNode question : result.path("questions")) {
                if (blank(question, "input") || !question.path("options").isArray() || question.path("options").isEmpty()) {
                    return "질문에 갈리는 입력이나 선택지가 없습니다.";
                }
                for (JsonNode option : question.path("options")) {
                    if (blank(option, "choice") || blank(option, "result")) return "선택지에 선택이나 그 결과가 없습니다.";
                }
            }
            for (JsonNode changed : result.path("changed_files")) {
                if (blank(changed, "path") || blank(changed, "role")) return "변경 파일에 경로나 역할이 없습니다.";
            }
            for (JsonNode check : result.path("checks")) {
                if (blank(check, "what") || blank(check, "command") || !check.path("evidence").isTextual()
                        || !List.of("pass", "fail", "not_run").contains(check.path("result").asText(""))) {
                    return "검사 기록에 대상·명령·결과·근거가 갖춰지지 않았습니다.";
                }
            }
            for (JsonNode item : result.path("unchecked")) {
                if (!item.isTextual()) return "unchecked의 항목이 문자열이 아닙니다.";
            }
            if (status.equals("stopped") && result.path("questions").isEmpty()) return "멈췄는데 질문이 없습니다.";
            if (status.equals("done") && result.path("pr_body").asText().isBlank()) return "끝났는데 풀 리퀘스트 본문 초안이 없습니다.";
            if (status.equals("done") && result.path("checks").isEmpty()) return "끝났는데 실행한 검사의 기록이 없습니다.";
            return null;
        }

        private static boolean blank(JsonNode node, String name) {
            return !node.path(name).isTextual() || node.path(name).asText().isBlank();
        }

        static boolean stopped(Path file) {
            try { return "stopped".equals(JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)).path("status").asText()); }
            catch (IOException | RuntimeException unreadable) { return false; }
        }
    }

    private static void pause() {
        try { Thread.sleep(100); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
