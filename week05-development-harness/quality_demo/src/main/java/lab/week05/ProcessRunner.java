package lab.week05;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 운영체제 입출력 보조 코드. 도구 호출·모델 대화는 Codex가 처리한다. */
public final class ProcessRunner {
    public record Result(int exitCode, String output, boolean timedOut) {}
    public Result run(List<String> command, Path workdir, String input, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).directory(workdir.toFile()).redirectErrorStream(true).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        var output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (var stream = process.inputReader(StandardCharsets.UTF_8)) {
                char[] chunk = new char[2048]; int count;
                while ((count = stream.read(chunk)) != -1) synchronized (output) {
                    output.append(chunk, 0, count);
                    if (output.length() > 100_000) output.delete(0, output.length() - 100_000);
                }
            } catch (IOException ignored) { /* 종료 시 닫힌 파이프에는 추가 행동이 없다. */ }
        }, "harness-output");
        reader.setDaemon(true); reader.start();
        var writeFailure = new AtomicReference<IOException>();
        Thread writer = new Thread(() -> {
            try (var stdin = process.outputWriter(StandardCharsets.UTF_8)) { stdin.write(input); }
            catch (IOException error) { writeFailure.set(error); }
        }, "harness-input");
        writer.setDaemon(true); writer.start();
        boolean finished = process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        writer.join(2000);
        reader.join(2000);
        synchronized (output) {
            int exit = finished ? process.exitValue() : 124;
            if (finished && writeFailure.get() != null && exit == 0) {
                exit = 1;
                output.append("\n요청 전달 실패: ").append(writeFailure.get().getMessage());
            }
            return new Result(exit, output.toString(), !finished);
        }
    }
}
