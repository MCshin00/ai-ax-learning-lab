package lab.week05.harness;

import lab.week05.ProcessRunner;

/** 준비, 작업, 검사, 필요한 복구를 호출 순서로 연결하는 작은 실행 엔진. */
public final class DevelopmentHarness {
    public enum Status { SUCCEEDED, NEEDS_INPUT, STOPPED }
    public enum Stage { PREPARE, EXECUTE, VERIFY }
    public record Outcome(Status status, Stage stage, int attempts, String reason) {}
    public enum ResultKind { OK, FAILED, UNAVAILABLE, TIMED_OUT }
    public record StepResult(ResultKind kind, int exitCode, String output) {
        public static StepResult fromProcess(ProcessRunner.Result raw) {
            ResultKind kind = raw.timedOut() ? ResultKind.TIMED_OUT
                : raw.exitCode() == 0 ? ResultKind.OK : ResultKind.UNAVAILABLE;
            return new StepResult(kind, raw.exitCode(), raw.output());
        }
    }
    @FunctionalInterface
    public interface StepRunner {
        StepResult run(Stage stage, String input, int attempt) throws Exception;
    }

    public Outcome run(WorkRequest request, StepRunner runner) {
        Stage stage = Stage.PREPARE;
        int attempts = 0;
        try {
            String original = request.prompt();
            String prompt = original;
            while (attempts <= request.maxRepairs()) {
                attempts++;
                stage = Stage.EXECUTE;
                StepResult work = runner.run(stage, prompt, attempts);
                if (!passed(work)) return new Outcome(Status.STOPPED, stage, attempts, evidence(work));
                stage = Stage.VERIFY;
                StepResult check = runner.run(stage, "", attempts);
                if (passed(check)) return new Outcome(Status.SUCCEEDED, stage, attempts, evidence(check));
                if (check.kind() != ResultKind.FAILED || attempts > request.maxRepairs()) {
                    return new Outcome(Status.STOPPED, stage, attempts, evidence(check));
                }
                prompt = original + "\n\n이번 작업 뒤 독립 검사가 실패했습니다.\n" + evidence(check)
                    + "\n원래 요구와 실패 근거를 대조해 필요한 부분을 수정하세요."
                    + " 통과시키려고 요구나 기대값을 낮추지 마세요."
                    + " 해결할 정보가 부족하면 필요한 내용을 알려 주세요.";
            }
            throw new IllegalStateException("실행 상한 이후의 상태를 확인해야 합니다.");
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            Status status = stage == Stage.PREPARE ? Status.NEEDS_INPUT : Status.STOPPED;
            return new Outcome(status, stage, attempts, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }
    private static boolean passed(StepResult result) {
        return result.kind() == ResultKind.OK;
    }
    private static String evidence(StepResult result) {
        String output = result.output() == null ? "" : result.output();
        String tail = output.substring(Math.max(0, output.length() - 2500));
        return (result.kind() + " (종료 코드 " + result.exitCode() + ")")
            + (tail.isBlank() ? "" : "\n" + tail);
    }
}
