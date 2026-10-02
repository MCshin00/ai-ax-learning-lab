package lab.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class SessionStartHook {
    private static final Pattern EVENT_NAME = Pattern.compile("\"hook_event_name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SOURCE = Pattern.compile("\"source\"\\s*:\\s*\"([^\"]*)\"");

    private SessionStartHook() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: SessionStartHook <project-root>");
        String event = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        System.out.print(context(event, Path.of(args[0])));
    }

    static String context(String event, Path projectRoot) throws IOException {
        if (!"SessionStart".equals(value(EVENT_NAME, event))) {
            throw new IllegalArgumentException("Expected a SessionStart event");
        }
        String source = value(SOURCE, event);
        if (!"startup".equals(source) && !"resume".equals(source)) {
            throw new IllegalArgumentException("Expected startup or resume source");
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        Path project = root.resolve("docs/project.md");
        String description = Files.readString(project, StandardCharsets.UTF_8);
        StringBuilder context = new StringBuilder("프로젝트 설명 (docs/project.md):\n")
            .append(description.strip()).append('\n');

        Path checkpoint = root.resolve(".local/harness/checkpoint.md");
        if (Files.isRegularFile(checkpoint)) {
            context.append("\n인계 메모는 작업 당시의 기록입니다. 현재 코드와 검사 결과를 대조해 남은 작업을 찾으세요.\n")
                .append("인계 메모 (.local/harness/checkpoint.md):\n")
                .append(Files.readString(checkpoint, StandardCharsets.UTF_8).strip()).append('\n');
        }
        return context.toString();
    }

    private static String value(Pattern pattern, String event) {
        var match = pattern.matcher(event);
        if (!match.find()) throw new IllegalArgumentException("Missing SessionStart event field");
        return match.group(1);
    }
}
