package lab.week05.harness;

import com.google.gson.*;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lab.week05.ProcessRunner;
import lab.week05.harness.DevelopmentHarness.*;

/** 요청 파일에서 자신의 순서 엔진을 시작하고 이번 결과를 별도 폴더에 남긴다. */
public final class HarnessCli {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private HarnessCli() {}
    static int run(Path requestFile, boolean demo, Path reports, PrintStream output) {
        String runId = UUID.randomUUID().toString();
        Path directory = reports.toAbsolutePath().normalize().resolve(runId);
        Outcome outcome;
        WorkRequest request = null;
        try {
            Files.createDirectories(directory);
            request = WorkRequest.load(requestFile);
            StepRunner runner = demo ? demoRunner() : processRunner(request, directory);
            outcome = new DevelopmentHarness().run(request, runner);
        } catch (Exception failure) {
            outcome = new Outcome(Status.NEEDS_INPUT, Stage.PREPARE, 0,
                failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        JsonObject report = new JsonObject();
        report.addProperty("runId", runId);
        report.addProperty("mode", demo ? "SIMULATED_RESPONSE" : "CODEX_PROCESS");
        report.add("outcome", JSON.toJsonTree(outcome));
        JsonObject execution = new JsonObject();
        execution.addProperty("source", request == null ? "UNAVAILABLE"
            : request.execution() == null ? "DEFAULTS" : "EXPLICIT");
        if (request != null && request.execution() != null) {
            execution.addProperty("model", request.execution().model());
            execution.addProperty("reasoningEffort", request.execution().reasoningEffort());
        }
        // 요청 조건이며 서비스가 실제 적용한 모델을 관측한 값은 아니다.
        report.add("requestedExecution", execution);
        report.addProperty("resultFile", directory.resolve("result.json").toString());
        try {
            Files.writeString(directory.resolve("result.json"), JSON.toJson(report), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            report.addProperty("recordingError", failure.getMessage());
            output.println(JSON.toJson(report));
            return 1;
        }
        output.println(JSON.toJson(report));
        return outcome.status() == Status.SUCCEEDED ? 0 : outcome.status() == Status.NEEDS_INPUT ? 2 : 1;
    }
    private static StepRunner processRunner(WorkRequest request, Path directory) {
        return processRunner(request, directory, new ProcessRunner()::run, new GradleVerifier()::run);
    }
    @FunctionalInterface
    interface ProcessCall {
        ProcessRunner.Result run(List<String> command, Path workspace, String input, int timeout) throws Exception;
    }
    @FunctionalInterface
    interface VerifyCall {
        StepResult run(List<String> command, Path workspace, int timeout, Path reports) throws Exception;
    }
    static StepRunner processRunner(WorkRequest request, Path directory, ProcessCall processes, VerifyCall verifier) {
        return (stage, input, attempt) -> {
            if (stage == Stage.VERIFY) {
                return verifier.run(request.verify(), request.workspace(), request.timeoutSeconds(),
                    directory.resolve("check-" + attempt));
            }
            return StepResult.fromProcess(processes.run(codexCommand(directory.resolve("agent-" + attempt + ".txt"), request.execution()),
                request.workspace(), input, request.timeoutSeconds()));
        };
    }
    static List<String> codexCommand(Path messageFile, WorkRequest.ExecutionSettings execution) {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").startsWith("Windows")) {
            command.addAll(List.of("cmd", "/d", "/c", "codex.cmd"));
        } else command.add("codex");
        command.addAll(List.of("-a", "never", "exec", "--sandbox", "workspace-write",
            "--ephemeral", "--color", "never", "-o", messageFile.toString()));
        if (execution != null) {
            command.addAll(List.of("--model", execution.model(), "-c",
                "model_reasoning_effort=" + execution.reasoningEffort()));
        }
        command.add("-");
        return List.copyOf(command);
    }
    private static StepRunner demoRunner() {
        return (stage, input, attempt) -> {
            if (stage == Stage.EXECUTE) return new StepResult(ResultKind.OK, 0, "교육용 완료 응답");
            return attempt == 1 ? new StepResult(ResultKind.FAILED, 1, "누락 id: invalid-waiver")
                : new StepResult(ResultKind.OK, 0, "교육용 검사 응답: 요청 8개의 결과가 일치합니다.");
        };
    }
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("사용법: development-harness.jar <요청.json> [--demo-repair]");
            return;
        }
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !args[1].equals("--demo-repair"))) {
            System.err.println("사용법: development-harness.jar <요청.json> [--demo-repair]");
            System.exit(2);
        }
        try {
            System.exit(run(Path.of(args[0]), args.length == 2,
                Path.of(".local/development-harness"), System.out));
        } catch (InvalidPathException failure) {
            System.err.println("요청 파일 경로를 확인하세요: " + failure.getMessage());
            System.exit(2);
        }
    }
}
