package lab.harness.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 프로세스 하나를 시작해 끝까지 지켜본다. 5주차의 ProcessRunner에서 가져왔다.
 * 요청을 표준 입력에 쓰고 닫는 것과 제한 시간은 그대로이고, 중단 요청, 하위 프로세스 추적,
 * 종료 요청 뒤 실제로 사라졌는지의 확인을 더했다.
 */
public final class ProcessRunner {
    public enum End { EXITED, TIMED_OUT, INTERRUPTED, START_FAILED }

    /** terminationConfirmed가 false이면 시작한 프로세스나 그 하위 프로세스가 남아 있을 수 있다. */
    public record Result(End end, int exitCode, boolean inputDelivered, boolean terminationConfirmed,
                         List<Seen> processes, String note) {}

    public enum Liveness { SAME, GONE, UNKNOWN }

    /** 프로세스 번호가 다시 쓰여도 구별하도록 시작 시각을 함께 둔다. */
    public record Seen(long pid, String startedAt) {
        static Seen of(ProcessHandle handle) {
            return new Seen(handle.pid(), handle.info().startInstant().map(Object::toString).orElse(""));
        }

        /** 같은 번호의 프로세스가 살아 있는데 시작 시각을 비교할 수 없으면 같은지 알 수 없다. */
        public Liveness liveness() {
            Optional<ProcessHandle> current = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
            if (current.isEmpty()) return Liveness.GONE;
            String now = of(current.get()).startedAt();
            if (startedAt.isEmpty() || now.isEmpty()) return Liveness.UNKNOWN;
            return startedAt.equals(now) ? Liveness.SAME : Liveness.GONE;
        }
    }

    /** 기록된 프로세스 가운데 지금 남아 있는 것. unknown이 true이면 같은 프로세스인지 가릴 수 없는 것이 있다. */
    public record Remaining(List<ProcessHandle> processes, boolean unknown) {
        public boolean none() { return processes.isEmpty() && !unknown; }
    }

    /** 기록된 프로세스와, 그것들이 지금 갖고 있는 하위 프로세스를 함께 찾는다. */
    public static Remaining remaining(List<Seen> recorded) {
        Map<Long, ProcessHandle> found = new LinkedHashMap<>();
        boolean unknown = false;
        for (Seen seen : recorded) {
            Liveness liveness = seen.liveness();
            if (liveness == Liveness.UNKNOWN) unknown = true;
            if (liveness != Liveness.SAME) continue;
            ProcessHandle.of(seen.pid()).filter(handle -> seen.startedAt().equals(Seen.of(handle).startedAt()))
                .ifPresent(handle -> found.putIfAbsent(handle.pid(), handle));
        }
        for (ProcessHandle handle : List.copyOf(found.values())) {
            handle.descendants().forEach(child -> found.putIfAbsent(child.pid(), child));
        }
        return new Remaining(List.copyOf(found.values()), unknown);
    }

    @FunctionalInterface public interface StopSignal { boolean requested(); }
    /** 지금까지 본 프로세스 전부를 받는다. 새 프로세스를 찾을 때마다 불리고, 종료를 요청하기 직전에 찾은 것도 알린다. */
    @FunctionalInterface public interface Watcher { void seen(List<Seen> processes); }

    /** 종료를 요청하고 grace 안에 모두 사라졌는지 돌려준다. */
    @FunctionalInterface public interface Terminator {
        boolean terminate(Collection<ProcessHandle> processes, Duration grace);
    }

    public static final Terminator FORCE = (processes, grace) -> {
        List<ProcessHandle> targets = new ArrayList<>(processes);
        for (int i = targets.size() - 1; i >= 0; i--) targets.get(i).destroyForcibly();
        return gone(targets, grace);
    };

    static boolean gone(Collection<ProcessHandle> processes, Duration grace) {
        long deadline = System.nanoTime() + grace.toNanos();
        while (processes.stream().anyMatch(ProcessHandle::isAlive)) {
            if (System.nanoTime() >= deadline) return false;
            try { Thread.sleep(50); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }

    private final Terminator terminator;
    private final Duration grace;

    public ProcessRunner() { this(FORCE, Duration.ofSeconds(10)); }

    public ProcessRunner(Terminator terminator, Duration grace) {
        this.terminator = terminator;
        this.grace = grace;
    }

    public Result run(List<String> command, Path workdir, Map<String, String> environment, String input,
                      Path logFile, Duration timeout, StopSignal stop, Watcher watcher) {
        Process process;
        try {
            Files.createDirectories(logFile.getParent());
            ProcessBuilder builder = new ProcessBuilder(command).directory(workdir.toFile())
                .redirectErrorStream(true).redirectOutput(logFile.toFile());
            builder.environment().putAll(environment);
            process = builder.start();
        } catch (IOException | RuntimeException failure) {
            return new Result(End.START_FAILED, -1, false, true, List.of(), failure.getMessage());
        }

        var writeFailure = new AtomicReference<IOException>();
        Thread writer = new Thread(() -> {
            try (var stdin = process.outputWriter(StandardCharsets.UTF_8)) { stdin.write(input); }
            catch (IOException failure) { writeFailure.set(failure); }
        }, "worker-input");
        writer.setDaemon(true);
        writer.start();

        // 번호와 시작 시각의 쌍으로 추적한다. 끝난 하위 프로세스의 번호를 새 하위 프로세스가 받아도 새 것을 놓치지 않는다.
        Map<Seen, ProcessHandle> tracked = new LinkedHashMap<>();
        track(process.toHandle(), tracked);
        long deadline = System.nanoTime() + timeout.toNanos();
        End end = End.EXITED;
        String failure = "";
        // 시작한 뒤에는 어떤 오류가 나도 아래의 종료 확인을 거친다. 기록을 남기지 못한 프로세스를 실행 중인 채로 두지 않는다.
        try {
            watcher.seen(List.copyOf(tracked.keySet()));
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                // 부모가 먼저 끝나면 하위 프로세스를 다시 찾기 어려우므로 살아 있는 동안 계속 적어 둔다.
                if (sweep(tracked)) watcher.seen(List.copyOf(tracked.keySet()));
                if (stop.requested()) { end = End.INTERRUPTED; break; }
                if (System.nanoTime() >= deadline) { end = End.TIMED_OUT; break; }
            }
            if (sweep(tracked)) watcher.seen(List.copyOf(tracked.keySet()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            end = End.INTERRUPTED;
        } catch (RuntimeException broken) {
            end = End.INTERRUPTED;
            failure = "실행을 지켜보다 오류가 나서 종료했습니다: " + broken.getMessage();
        }

        // 지켜보다 오류로 빠져나온 경우에도 종료를 요청하기 전에 하위 프로세스를 한 번 더 찾고, 새로 찾은 것을 알린다.
        // 종료에 실패해도 받는 쪽의 기록에 마지막으로 본 프로세스가 모두 남는다. 알리다 난 오류는 받는 쪽이 다룬다.
        try { if (sweep(tracked)) watcher.seen(List.copyOf(tracked.keySet())); }
        catch (RuntimeException broken) { if (failure.isEmpty()) failure = "프로세스를 기록하다 오류가 났습니다: " + broken.getMessage(); }

        // 정상 종료여도 남은 하위 프로세스가 있으면 같은 폴더를 계속 고칠 수 있으므로 함께 끝낸다.
        boolean leftovers = tracked.values().stream().anyMatch(ProcessHandle::isAlive);
        boolean confirmed = true;
        if (leftovers) {
            // 종료를 요청하다 오류가 나면 끝났다고 볼 근거가 없다.
            try { confirmed = terminator.terminate(tracked.values(), grace); }
            catch (RuntimeException broken) { confirmed = false; }
        }
        try { writer.join(2000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }

        int exit = end == End.EXITED && !process.isAlive() ? process.exitValue() : -1;
        boolean delivered = writeFailure.get() == null && !writer.isAlive();
        String note = !confirmed ? "종료를 요청했지만 프로세스가 남아 있습니다."
            : !failure.isEmpty() ? failure
            : !delivered ? "요청을 끝까지 전달하지 못했습니다."
            : leftovers && end == End.EXITED ? "종료 뒤 남아 있던 하위 프로세스를 끝냈습니다." : "";
        return new Result(end, exit, delivered, confirmed, List.copyOf(tracked.keySet()), note);
    }

    // 알고 있는 프로세스 각각의 하위 프로세스를 다시 찾는다. 새로 찾은 것이 있으면 true.
    private static boolean sweep(Map<Seen, ProcessHandle> tracked) {
        int before = tracked.size();
        for (ProcessHandle known : List.copyOf(tracked.values())) {
            known.descendants().forEach(child -> track(child, tracked));
        }
        return tracked.size() != before;
    }

    // 시작 시각은 살아 있을 때만 읽을 수 있다. 읽기 전에 이미 끝난 프로세스는 남아 있을 수 없으므로 적지 않는다.
    private static void track(ProcessHandle handle, Map<Seen, ProcessHandle> tracked) {
        Seen seen = Seen.of(handle);
        if (seen.startedAt().isEmpty() && !handle.isAlive()) return;
        tracked.putIfAbsent(seen, handle);
    }
}
