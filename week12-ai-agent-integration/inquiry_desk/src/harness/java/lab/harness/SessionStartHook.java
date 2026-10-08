package lab.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class SessionStartHook {
    private static final Pattern EVENT_NAME = Pattern.compile("\"hook_event_name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SOURCE = Pattern.compile("\"source\"\\s*:\\s*\"([^\"]*)\"");
    static final String REST_STARTS_AT = "## 개발 환경";

    private SessionStartHook() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: SessionStartHook <project-root>");
        String event = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        // 표준 출력의 기본 문자 집합이 UTF-8이 아닌 환경에서도 한글이 깨지지 않게 바이트로 쓴다.
        System.out.write(context(event, Path.of(args[0])).getBytes(StandardCharsets.UTF_8));
        System.out.flush();
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
        String description = Files.readString(project, StandardCharsets.UTF_8).replace("\r\n", "\n");
        // 업무 요구·처리 순서·지금 구현된 것·제공 자료까지 전달한다. 개발 환경 절부터는 필요할 때 파일에서 읽게 한다.
        int cut = description.indexOf("\n" + REST_STARTS_AT);
        String head = cut < 0 ? description : description.substring(0, cut);
        return "프로젝트 설명 (docs/project.md):\n" + head.strip() + "\n\n"
            + "개발 환경과 작업자 실행 관리의 설명은 docs/project.md의 뒷부분에, 정해진 결정과 이유는 docs/adr/에 있습니다.\n";
    }

    private static String value(Pattern pattern, String event) {
        var match = pattern.matcher(event);
        if (!match.find()) throw new IllegalArgumentException("Missing SessionStart event field");
        return match.group(1);
    }
}
