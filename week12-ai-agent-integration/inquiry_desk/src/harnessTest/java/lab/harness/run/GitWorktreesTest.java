package lab.harness.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GitWorktreesTest {
    @TempDir Path temp;

    private static void git(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString(),
            "-c", "user.name=test", "-c", "user.email=test@example.invalid"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, process.waitFor(), String.join(" ", arguments));
    }

    @Test void eachTaskGetsItsOwnFolderAndBranchFromTheBaseCommit() throws Exception {
        Path repository = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectories(repository.resolve("app"));
        Files.writeString(repository.resolve("app/file.txt"), "기준");
        // Hook 등록은 Git으로 공유하고, 로컬 기록은 Git에서 제외한다.
        Files.write(repository.resolve(".gitignore"), List.of(".local/"));
        Files.createDirectories(repository.resolve("app/.codex"));
        Files.writeString(repository.resolve("app/.codex/hooks.json"), "{}");
        Files.createDirectories(repository.resolve("app/.local"));
        Files.writeString(repository.resolve("app/.local/record.json"), "{}");
        git(repository, "init", "-q", "-b", "main");
        git(repository, "add", ".");
        git(repository, "commit", "-q", "-m", "base");
        // 커밋하지 않은 변경은 새 작업 폴더로 전달되지 않는다.
        Files.writeString(repository.resolve("app/file.txt"), "커밋하지 않은 변경");

        var worktrees = new Workspaces.GitWorktrees(repository, temp.resolve("trees"), "app", "main");
        Path first = worktrees.prepare("one");
        Path second = worktrees.prepare("two");
        assertNotEquals(first, second);
        assertEquals("기준", Files.readString(first.resolve("file.txt")));
        // 새 작업 폴더에도 Hook 등록이 있고, 원래 폴더의 로컬 기록은 따라오지 않는다.
        assertTrue(Files.exists(first.resolve(".codex/hooks.json")));
        assertFalse(Files.exists(first.resolve(".local")));

        // 한 작업 폴더의 변경은 다른 작업 폴더와 원래 폴더에 보이지 않는다.
        Files.writeString(first.resolve("file.txt"), "작업 one의 변경");
        assertEquals("기준", Files.readString(second.resolve("file.txt")));
        assertEquals("커밋하지 않은 변경", Files.readString(repository.resolve("app/file.txt")));

        // 같은 작업의 다음 시도는 같은 폴더를 이어서 쓴다.
        assertEquals(first, worktrees.prepare("one"));
        assertEquals("작업 one의 변경", Files.readString(first.resolve("file.txt")));

        // 이미 있는 폴더가 이 저장소의 worktree가 아니면 쓰지 않는다. 만들다 만 폴더가 그런 경우다.
        Files.createDirectories(temp.resolve("trees/half/app"));
        assertThrows(IllegalStateException.class, () -> worktrees.prepare("half"));
        // 다른 작업의 브랜치로 만들어진 worktree도 쓰지 않는다.
        git(repository, "worktree", "add", "-q", "-b", "task/other", temp.resolve("trees/mine").toString(), "main");
        assertThrows(IllegalStateException.class, () -> worktrees.prepare("mine"));

        // 프로젝트 경로가 작업 폴더 밖을 가리키는 설정은 폴더를 만들기 전에 거절한다.
        for (String outside : List.of("../shared", temp.resolve("repo/app").toString())) {
            var escaping = new Workspaces.GitWorktrees(repository, temp.resolve("trees"), outside, "main");
            assertThrows(IllegalStateException.class, () -> escaping.prepare("three"));
        }
        assertFalse(Files.exists(temp.resolve("trees/three")));
    }
}
