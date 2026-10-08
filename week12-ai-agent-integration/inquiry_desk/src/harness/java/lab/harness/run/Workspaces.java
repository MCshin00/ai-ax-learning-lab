package lab.harness.run;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 작업마다 따로 쓰는 프로젝트 폴더를 준비한다. 같은 작업의 다음 시도는 같은 폴더를 이어서 쓴다. */
@FunctionalInterface
public interface Workspaces {
    Path prepare(String taskId) throws Exception;

    /** 작업 폴더를 만들던 프로세스의 종료를 확인하지 못했다. 그 프로세스가 같은 폴더를 계속 만들고 있을 수 있다. */
    final class StillRunning extends Exception {
        public StillRunning(String message) { super(message); }
    }

    /**
     * 기준 커밋에서 작업별 브랜치와 worktree를 만든다. Hook 등록(.codex/hooks.json)은 경로를 프로젝트 기준
     * 상대경로로만 적어 Git으로 공유하므로 새 폴더에도 그대로 있고, 그 폴더의 코드를 검사한다.
     */
    final class GitWorktrees implements Workspaces {
        private final Path repository;
        private final Path base;
        private final String projectPath;
        private final String baseRef;

        public GitWorktrees(Path repository, Path base, String projectPath, String baseRef) {
            this.repository = repository.toAbsolutePath().normalize();
            this.base = base.toAbsolutePath().normalize();
            this.projectPath = projectPath;
            this.baseRef = baseRef;
        }

        @Override public Path prepare(String taskId) throws Exception {
            String branch = "task/" + taskId;
            Path tree = base.resolve(taskId);
            Path project = tree.resolve(projectPath).normalize();
            // 프로젝트 경로가 작업 폴더를 벗어나면 서로 다른 작업이 같은 폴더를 고치게 된다. 폴더를 만들기 전에 거절한다.
            if (!project.startsWith(tree)) throw new IllegalStateException("프로젝트 경로가 작업 폴더 밖을 가리킵니다: " + projectPath);
            if (Files.exists(tree)) {
                // 이미 있는 폴더는 이 저장소가 이 작업의 브랜치로 만든 worktree일 때만 이어서 쓴다.
                // 만들다 만 폴더나 다른 저장소의 폴더에서 작업자를 실행하지 않는다.
                if (!registered(tree, branch)) {
                    throw new IllegalStateException("작업 폴더가 이 저장소의 " + branch + " worktree가 아닙니다: " + tree.getFileName());
                }
            } else {
                Files.createDirectories(base);
                boolean exists = !git(repository, "branch", "--list", branch).isBlank();
                if (exists) git(repository, "worktree", "add", tree.toString(), branch);
                else git(repository, "worktree", "add", "-b", branch, tree.toString(), baseRef);
            }
            if (!Files.isDirectory(project)) throw new IllegalStateException("작업 폴더에 프로젝트가 없습니다: " + projectPath);
            // 링크를 따라간 실제 위치도 작업 폴더 안이어야 한다.
            if (!project.toRealPath().startsWith(tree.toRealPath())) {
                throw new IllegalStateException("프로젝트 경로가 작업 폴더 밖을 가리킵니다: " + projectPath);
            }
            return project;
        }

        // 저장소의 worktree 목록에 이 폴더가 이 브랜치로 올라 있는지 본다.
        private boolean registered(Path tree, String branch) throws Exception {
            Path listed = null;
            for (String line : git(repository, "worktree", "list", "--porcelain").split("\\R")) {
                if (line.startsWith("worktree ")) listed = Path.of(line.substring("worktree ".length()));
                else if (line.equals("branch refs/heads/" + branch) && listed != null
                        && Files.exists(listed) && Files.isSameFile(listed, tree)) return true;
            }
            return false;
        }

        private static String git(Path directory, String... arguments) throws Exception {
            List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
            command.addAll(List.of(arguments));
            Path log = Files.createTempFile("worktree-git", ".log");
            String output;
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                process.getOutputStream().close();
                boolean finished;
                boolean interrupted = false;
                try { finished = process.waitFor(120, TimeUnit.SECONDS); }
                catch (InterruptedException stopped) { interrupted = true; finished = false; }
                if (!finished) {
                    // 끝나지 않은 git을 두고 돌아가면 다음 접수와 같은 폴더를 함께 만들 수 있으므로 사라진 것까지 확인한다.
                    List<ProcessHandle> all = new ArrayList<>(process.descendants().toList());
                    all.add(process.toHandle());
                    String what = "git " + String.join(" ", arguments);
                    boolean gone;
                    try { gone = ProcessRunner.FORCE.terminate(all, Duration.ofSeconds(10)); }
                    catch (RuntimeException broken) { gone = false; }
                    if (interrupted) Thread.currentThread().interrupt();
                    if (!gone) throw new StillRunning(what + "이(가) 끝나지 않았고 종료도 확인하지 못했습니다.");
                    throw new IllegalStateException(what + "이(가) 끝나지 않아 종료했습니다.");
                }
                output = Files.readString(log, StandardCharsets.UTF_8).strip();
                if (process.exitValue() == 0) return output;
            } finally {
                // 남은 프로세스가 로그 파일을 쥐고 있으면 지우지 못한다. 그 오류가 위에서 던진 이유를 가리지 않게 한다.
                try { Files.deleteIfExists(log); }
                catch (java.io.IOException inUse) { /* 임시 파일이라 남아도 된다. */ }
            }
            throw new IllegalStateException("git " + String.join(" ", arguments) + ": " + output);
        }
    }
}
