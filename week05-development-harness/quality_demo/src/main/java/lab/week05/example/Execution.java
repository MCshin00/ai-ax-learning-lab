package lab.week05.example;

import lab.week05.ProcessRunner;
import java.nio.file.Path;
import java.util.List;

/** 도구의 결과를 이 예제 엔진이 이해하는 사건으로 바꾸는 경계. */
public final class Execution {
    private Execution() {}
    public enum Kind { OK, FAILED, TIMED_OUT, COULD_NOT_START }
    public record Result(Kind kind, int exitCode, String output) {}
    @FunctionalInterface public interface Executor {
        Result run(List<String> command, Path workspace, String input, int seconds);
    }
    public static Executor process() {
        return (command, workspace, input, seconds) -> {
            try {
                var result = new ProcessRunner().run(command, workspace, input, seconds);
                return new Result(result.timedOut() ? Kind.TIMED_OUT : result.exitCode() == 0 ? Kind.OK : Kind.FAILED,
                    result.exitCode(), result.output());
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                return new Result(Kind.COULD_NOT_START, -1, error.getMessage());
            }
        };
    }
    public static String evidence(Result result) {
        String output = result.output() == null ? "" : result.output();
        return result.kind() + " (exit=" + result.exitCode() + "): "
            + output.substring(Math.max(0, output.length() - 2500));
    }
    public static String repairPrompt(String original, Result failure) {
        return original + "\n\n독립 검사가 실패했습니다. 근거에서 원인을 확인하고 필요한 수정을 수행하세요. "
            + "요구와 기대값을 낮추지 마세요. 해결할 근거가 없으면 필요한 정보를 알려 주세요.\n"
            + evidence(failure);
    }
}
