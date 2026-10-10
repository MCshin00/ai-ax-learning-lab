package lab.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStartHookTest {
    @TempDir Path projectRoot;

    @Test void passesTheAppPartAndPointsToTheRest() throws Exception {
        write("docs/project.md", "# 앱\r\n\r\n## 업무 요구\r\n요구 여섯 가지\r\n\r\n## 개발 환경\r\n실행 관리의 긴 설명\r\n");
        // 예전에 쓰던 인계 메모가 남아 있어도 전달하지 않는다.
        write(".local/harness/checkpoint.md", "지난 작업의 메모");

        String context = SessionStartHook.context(event("startup"), projectRoot);

        assertTrue(context.contains("요구 여섯 가지"));
        assertFalse(context.contains("실행 관리의 긴 설명"));
        assertFalse(context.contains("지난 작업의 메모"));
        assertTrue(context.contains("docs/project.md의 뒷부분"));
        assertTrue(context.contains("docs/adr/"));
    }

    private String event(String source) {
        return "{\"hook_event_name\":\"SessionStart\",\"source\":\"" + source + "\"}";
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
