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
        // 작업자와 검사가 띄운 프로세스를 한 목록에 모아 기록한다. 실행 관리가 사라지면 stop이 이 목록으로 종료를 요청한다.
        // 번호와 시작 시각을 함께 식별자로 쓴다. 번호가 다시 쓰여도 새 프로세스의 기록을 버리지 않는다.
        Set<ProcessRunner.Seen> launched = new LinkedHashSet<>();
        ProcessRunner.Watcher watcher = processes -> {
            synchronized (launched) {
                launched.addAll(processes);
                ledger.recordProcesses(taskId, number, List.copyOf(launched));
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
            Path workspace = workspaces.prepare(taskId);
            ledger.recordWorkspace(taskId, workspace);
            Path resultFile = directory.resolve("result.json");
            List<String> command = settings.workerCommand().stream().map(part -> part
                .replace("{workspace}", workspace.toString()).replace("{result}", resultFile.toString())).toList();
            // 종료 Hook이 이번 실행에서 남긴 판정만 가져오도록 앞선 실행의 기록을 지운다.
            Path hookRecord = workspace.resolve(".local/harness/last-hook-event.json");
            Files.deleteIfExists(hookRecord);
            // 작업 폴더를 준비하는 동안 중단 요청이 들어왔으면 작업자를 시작하지 않는다.
            if (stop.requested()) throw new StopRequested("중단 요청이 있어 작업자를 시작하지 않았습니다.");
            confirmed = false;
            run = runner.run(command, workspace, settings.workerEnvironment(), request, directory.resolve("worker.log"),
                settings.workerTimeout(), stop, watcher);
            confirmed = run.terminationConfirmed();
            // Hook이 걸렸는지와 그 판정을 시도의 기록으로 남긴다. 파일이 없으면 Hook이 실행되지 않았거나 판정까지 가지 못한 것이다.
            if (Files.isRegularFile(hookRecord)) {
                Files.copy(hookRecord, directory.resolve("hook-event.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                hookNote = hookStopped(hookRecord);
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
                WorkerResult result = WorkerResult.read(resultFile);
                if (result.problem() != null) {
                    state = State.NEEDS_CHECK;
                    reason = result.problem();
                } else if (result.stopped()) {
                    state = State.QUESTION;
                    reason = "작업자가 질문을 남기고 멈췄습니다.";
                } else {
                    confirmed = false;
                    Check verdict = checker.run(workspace, directory.resolve("check"), stop, watcher);
                    confirmed = verdict.terminationConfirmed();
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
        TaskLedger.Lock lock = ledger.requestStop(taskId);
        if (lock == null) return current(taskId, "실행 중인 시도가 없습니다.");
        int number = lock.attempt();
        if (ledger.attempt(taskId, number) == null && lock.manager().liveness() != ProcessRunner.Liveness.GONE) {
            long deadline = System.nanoTime() + wait.toNanos();
            while (ledger.attempt(taskId, number) == null && System.nanoTime() < deadline) pause();
            return current(taskId, "중단을 요청했지만 시도가 아직 끝나지 않았습니다.");
        }
        ProcessRunner.Remaining remaining = ProcessRunner.remaining(ledger.processes(taskId, number));
        boolean gone = ProcessRunner.FORCE.terminate(remaining.processes(), wait) && !remaining.unknown();
        Attempt before = ledger.attempt(taskId, number);
        String note = gone ? "남아 있던 프로세스의 종료를 확인했습니다."
            : "종료를 요청했지만 프로세스가 남아 있거나 같은 프로세스인지 가릴 수 없습니다.";
        ledger.recordStop(taskId, new Attempt(number, before == null ? "" : before.startedAt(), Instant.now().toString(),
            before == null ? "ABANDONED" : before.end(), before == null ? -1 : before.exitCode(),
            before != null && before.inputDelivered(), gone, before == null ? "" : before.check(), note),
            note);
        return current(taskId, "");
    }

    /** 사람이 남은 프로세스가 없음을 확인한 시도의 잠금을 푼다. */
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

    /** 결과 형식은 작업자 명령과 요청문이 맡고, 여기서는 상태와 멈춘 결과의 질문 유무만 읽는다. */
    static record WorkerResult(boolean stopped, String problem) {
        static WorkerResult read(Path file) {
            JsonNode result;
            try { result = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)); }
            catch (IOException | RuntimeException unreadable) { return new WorkerResult(false, "결과 파일을 읽을 수 없습니다."); }
            if (result == null || !result.isObject()) return new WorkerResult(false, "결과가 JSON 객체가 아닙니다.");
            return switch (result.path("status").asText("")) {
                case "done" -> new WorkerResult(false, null);
                case "stopped" -> new WorkerResult(true,
                    result.path("questions").isEmpty() ? "멈췄는데 질문이 없습니다." : null);
                default -> new WorkerResult(false, "status가 done 또는 stopped가 아닙니다.");
            };
        }
    }

    private static void pause() {
        try { Thread.sleep(100); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
