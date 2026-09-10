package lab.week05.example;

import lab.week05.example.Execution.*;

/** 같은 업무를 순서 함수로 표현한 대안. 단계별 전이 기록은 만들지 않는다. */
public final class LinearEngine {
    public record Outcome(String status, int attempts, String reason) {}
    public Outcome run(Job job, Executor executor) {
        var missing = job.missingInformation();
        if (!missing.isEmpty()) return new Outcome("NEEDS_INPUT", 0, String.join("\n", missing));
        final String original;
        try { original = job.prompt(); }
        catch (Exception error) { return new Outcome("NEEDS_INPUT", 0, error.getMessage()); }
        String prompt = original;
        for (int attempt = 1; attempt <= 1 + job.repairLimit(); attempt++) {
            Result execution = executor.run(job.executeCommand(), job.workspace(), prompt, job.timeoutSeconds());
            if (execution.kind() != Kind.OK) return new Outcome("STOPPED", attempt, Execution.evidence(execution));
            Result verification = executor.run(job.verifyCommand(), job.workspace(), "", job.timeoutSeconds());
            if (verification.kind() == Kind.OK) return new Outcome("SUCCEEDED", attempt, Execution.evidence(verification));
            if (verification.kind() != Kind.FAILED || attempt > job.repairLimit()) {
                return new Outcome("STOPPED", attempt, Execution.evidence(verification));
            }
            prompt = Execution.repairPrompt(original, verification);
        }
        throw new IllegalStateException("도달할 수 없는 상태");
    }
}
