package lab.harness.run;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 작업 접수 기록. 작업마다 폴더 하나, 실행 시도마다 그 아래 폴더 하나를 둔다.
 * 잠금 파일이 있으면 그 작업의 시도 하나가 실행 권한을 갖고 있다는 뜻이고, 실행 관리가 종료를 확인하거나 사람이 release로 풀 때까지 남는다.
 * 잠금을 살피고, 풀고, 새로 만들고, 시도의 끝을 기록하는 일은 작업마다 한 번에 한 곳에서만 한다.
 */
public final class TaskLedger {
    public enum State { RUNNING, REVIEW_READY, CHECK_FAILED, CHECK_UNAVAILABLE, QUESTION, NEEDS_CHECK }

    public record Task(String taskId, State state, String reason, int attempts, String workspace) {}

    public record Attempt(int number, String startedAt, String endedAt, String end, int exitCode,
                          boolean inputDelivered, boolean terminationConfirmed, String check, String note) {}

    public record Lock(int attempt, ProcessRunner.Seen manager) {}

    public record Admission(boolean accepted, int attempt, State state, String reason) {}

    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Object GUARD = new Object();

    private final Path root;

    public TaskLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path taskDir(String taskId) {
        if (!taskId.matches("[a-z0-9][a-z0-9-]{0,60}")) throw new IllegalArgumentException("작업 이름은 영문 소문자·숫자·하이픈입니다.");
        return root.resolve(taskId);
    }

    public Path attemptDir(String taskId, int attempt) {
        return taskDir(taskId).resolve("attempts").resolve(Integer.toString(attempt));
    }

    /** 같은 작업의 앞선 시도가 끝났다고 확인된 경우에만 새 시도를 받아들인다. 접수와 실행 권한 확보가 여기서 함께 일어난다. */
    public Admission admit(String taskId) throws IOException {
        Files.createDirectories(taskDir(taskId).resolve("attempts"));
        return exclusively(taskId, () -> {
            Path lockFile = taskDir(taskId).resolve("lock");
            if (Files.exists(lockFile)) {
                return refusal(taskId);
            }
            int next = attempts(taskId) + 1;
            write(lockFile, new Lock(next, ProcessRunner.Seen.of(ProcessHandle.current())));
            try {
                // 이미 있는 시도 폴더는 쓰지 않는다. 앞선 시도의 요청·결과를 새 시도의 것으로 읽지 않기 위해서다.
                Files.createDirectory(attemptDir(taskId, next));
            } catch (FileAlreadyExistsException reused) {
                Files.deleteIfExists(lockFile);
                return new Admission(false, next, State.NEEDS_CHECK, next + "번째 시도의 폴더가 이미 있습니다.");
            }
            write(taskDir(taskId).resolve("task.json"), new Task(taskId, State.RUNNING, "", next, workspace(taskId)));
            return new Admission(true, next, State.RUNNING, "");
        });
    }

    private Admission refusal(String taskId) throws IOException {
        Lock lock = lock(taskId);
        int number = lock.attempt();
        if (attempt(taskId, number) == null && lock.manager().liveness() != ProcessRunner.Liveness.GONE) {
            return new Admission(false, number, State.RUNNING, "같은 작업의 " + number + "번째 시도가 실행 중입니다.");
        }
        return refuse(taskId, number, number + "번째 시도의 잠금이 남아 있습니다. stop " + taskId
            + " → 남은 프로세스가 없는지 확인 → release " + taskId + " " + number + " 순서로 복구한 뒤 start하세요.");
    }

    private Admission refuse(String taskId, int attempt, String reason) throws IOException {
        write(taskDir(taskId).resolve("task.json"), new Task(taskId, State.NEEDS_CHECK, reason, attempts(taskId), workspace(taskId)));
        return new Admission(false, attempt, State.NEEDS_CHECK, reason);
    }

    public void recordWorkspace(String taskId, Path workspace) throws IOException {
        exclusively(taskId, () -> {
            Task task = task(taskId);
            write(taskDir(taskId).resolve("task.json"), new Task(taskId, task.state(), task.reason(), task.attempts(), workspace.toString()));
            return null;
        });
    }

    /**
     * 실행 권한을 가진 시도에 중단 요청을 남기고 그 잠금을 돌려준다. 잠금이 없으면 null.
     * 접수가 잠금과 시도 폴더를 만드는 도중에 끼어들지 않도록 같은 배타 구간에서 한다.
     */
    public Lock requestStop(String taskId) throws IOException {
        return exclusively(taskId, () -> {
            Lock lock = lock(taskId);
            if (lock != null && Files.isDirectory(attemptDir(taskId, lock.attempt()))) {
                Files.writeString(stopFile(taskId, lock.attempt()), java.time.Instant.now().toString(), StandardCharsets.UTF_8);
            }
            return lock;
        });
    }

    public void recordProcesses(String taskId, int attempt, List<ProcessRunner.Seen> processes) {
        try { write(attemptDir(taskId, attempt).resolve("processes.json"), processes); }
        catch (IOException failure) { throw new IllegalStateException("프로세스 기록을 남기지 못했습니다.", failure); }
    }

    /** 종료가 확인되지 않은 시도는 잠금을 남겨 같은 작업의 새 실행을 막는다. 자기 시도의 잠금만 푼다. */
    public void finish(String taskId, Attempt attempt, State state, String reason) throws IOException {
        exclusively(taskId, () -> {
            write(attemptDir(taskId, attempt.number()).resolve("attempt.json"), attempt);
            if (attempt.number() == attempts(taskId)) {
                write(taskDir(taskId).resolve("task.json"), new Task(taskId, state, reason, attempts(taskId), workspace(taskId)));
            }
            Lock lock = lock(taskId);
            if (attempt.terminationConfirmed() && lock != null && lock.attempt() == attempt.number()) {
                Files.deleteIfExists(taskDir(taskId).resolve("lock"));
            }
            return null;
        });
    }

    /**
     * 남은 프로세스가 없다는 것을 사람이 확인한 뒤 잠금을 푼다.
     * attempt는 사람이 확인한 시도의 번호다. 그 사이 다른 시도가 접수됐거나,
     * 그 시도의 끝이 아직 기록되지 않았는데 지켜보는 실행 관리가 살아 있으면 풀지 않고 이유를 돌려준다. 풀었으면 null.
     */
    public String release(String taskId, int attempt) throws IOException {
        return exclusively(taskId, () -> {
            if (!Files.exists(taskDir(taskId).resolve("lock"))) return "풀 잠금이 없습니다.";
            Lock lock = lock(taskId);
            if (lock.attempt() != attempt) {
                return "지금 잠금은 " + lock.attempt() + "번째 시도의 것입니다. 확인한 시도와 다릅니다.";
            }
            if (attempt(taskId, attempt) == null && lock.manager().liveness() != ProcessRunner.Liveness.GONE) {
                return attempt + "번째 시도를 지켜보는 실행 관리가 살아 있습니다. stop을 쓰세요.";
            }
            String note = "사람이 남은 프로세스가 없음을 확인하고 잠금을 풀었습니다.";
            Attempt before = attempt(taskId, lock.attempt());
            write(attemptDir(taskId, lock.attempt()).resolve("attempt.json"), before == null
                ? new Attempt(lock.attempt(), "", "", "ABANDONED", -1, false, true, "", note)
                : new Attempt(before.number(), before.startedAt(), before.endedAt(), before.end(), before.exitCode(),
                    before.inputDelivered(), true, before.check(), (before.note() + " " + note).strip()));
            write(taskDir(taskId).resolve("task.json"), new Task(taskId, State.NEEDS_CHECK, note, attempts(taskId), workspace(taskId)));
            Files.deleteIfExists(taskDir(taskId).resolve("lock"));
            return null;
        });
    }

    /** 복구용 stop의 결과를 남긴다. 잠금은 사람이 release로 푼다. */
    public void recordStop(String taskId, Attempt attempt, String reason) throws IOException {
        exclusively(taskId, () -> {
            write(attemptDir(taskId, attempt.number()).resolve("attempt.json"), attempt);
            write(taskDir(taskId).resolve("task.json"), new Task(taskId, State.NEEDS_CHECK, reason, attempts(taskId), workspace(taskId)));
            return null;
        });
    }

    public Task task(String taskId) throws IOException {
        Path file = taskDir(taskId).resolve("task.json");
        return Files.isRegularFile(file) ? JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), Task.class) : null;
    }

    public Attempt attempt(String taskId, int attempt) throws IOException {
        Path file = attemptDir(taskId, attempt).resolve("attempt.json");
        return Files.isRegularFile(file) ? JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), Attempt.class) : null;
    }

    /** 잠금이 없으면 null. */
    public Lock lock(String taskId) throws IOException {
        Path file = taskDir(taskId).resolve("lock");
        return Files.exists(file) ? JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), Lock.class) : null;
    }

    public List<ProcessRunner.Seen> processes(String taskId, int attempt) throws IOException {
        Path file = attemptDir(taskId, attempt).resolve("processes.json");
        if (!Files.isRegularFile(file)) return List.of();
        return List.of(JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), ProcessRunner.Seen[].class));
    }

    public int attempts(String taskId) throws IOException {
        Path directory = taskDir(taskId).resolve("attempts");
        if (!Files.isDirectory(directory)) return 0;
        try (var entries = Files.list(directory)) {
            return (int) entries.filter(Files::isDirectory).count();
        }
    }

    public Path stopFile(String taskId, int attempt) {
        return attemptDir(taskId, attempt).resolve("stop-requested");
    }

    private String workspace(String taskId) throws IOException {
        Task task = task(taskId);
        return task == null ? "" : task.workspace();
    }

    @FunctionalInterface private interface Step<T> { T run() throws IOException; }

    /**
     * 살핀 뒤 지우는 사이에 다른 실행 관리가 새 잠금을 만들면 그 잠금을 지우게 되므로 한 번에 한 곳만 들어온다.
     * 운영체제의 파일 잠금이라 들고 있던 프로세스가 죽으면 풀린다.
     */
    private <T> T exclusively(String taskId, Step<T> step) throws IOException {
        Files.createDirectories(taskDir(taskId));
        synchronized (GUARD) {
            try (FileChannel channel = FileChannel.open(taskDir(taskId).resolve("ledger.guard"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var held = channel.lock()) {
                return step.run();
            }
        }
    }

    // 읽는 쪽이 반쯤 쓰인 파일을 보지 않도록 옆에 쓴 뒤 바꿔 놓는다.
    private static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, JSON.writeValueAsString(value), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
