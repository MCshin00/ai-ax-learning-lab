package lab.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStartHookTest {
    @TempDir Path projectRoot;

    @Test void withoutCheckpointReturnsProjectDescription() throws Exception {
        write("docs/project.md", "현재 처리와 검사 위치");

        String context = SessionStartHook.context(event("startup"), projectRoot);

        assertTrue(context.contains("프로젝트 설명 (docs/project.md):\n현재 처리와 검사 위치"));
        assertFalse(context.contains("인계 메모"));
    }

    @Test void withCheckpointReturnsBothAndRequiresCurrentCodeComparison() throws Exception {
        write("docs/project.md", "현재 처리와 검사 위치");
        write(".local/harness/checkpoint.md", "Hook 검사 완료, 앱 구현 남음");

        String context = SessionStartHook.context(event("resume"), projectRoot);

        assertTrue(context.contains("현재 처리와 검사 위치"));
        assertTrue(context.contains("Hook 검사 완료, 앱 구현 남음"));
        assertTrue(context.contains("현재 코드와 검사 결과를 대조해 남은 작업을 찾으세요"));
    }

    @Test void wrongEventDoesNotReadProjectFiles() {
        assertThrows(IllegalArgumentException.class, () -> SessionStartHook.context(
            "{\"hook_event_name\":\"Stop\",\"source\":\"startup\"}", projectRoot));
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
