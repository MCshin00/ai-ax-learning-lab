package lab.harness.run;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lab.harness.run.Checker.Check;
import lab.harness.run.TaskLedger.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class IssueWatchTest {
    @TempDir Path temp;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private static final String SUMMARY = "검사 83, 실패 0, 건너뜀 0.";

    private static class Issues implements IssueWatch.Issues {
        List<IssueWatch.Issue> listed = List.of(new IssueWatch.Issue(5, "옛 제목", "옛 본문"));
        String state = "OPEN";
        List<IssueWatch.Label> labels = List.of(new IssueWatch.Label("agent-ready"));
        boolean failList;
        boolean failComment;
        final List<Integer> viewed = new ArrayList<>();
        final List<Integer> commented = new ArrayList<>();
        final List<String> comments = new ArrayList<>();

        @Override public List<IssueWatch.Issue> list(String label) throws IOException {
            assertEquals("agent-ready", label);
            if (failList) throw new IOException("조회 대역 실패");
            return listed;
        }
        @Override public IssueWatch.Detail view(int number) {
            viewed.add(number);
            return new IssueWatch.Detail(number, "현재 제목", "현재 본문\n둘째 줄", state, labels);
        }
        @Override public void comment(int number, String body) throws IOException {
            if (failComment) throw new IOException("댓글 대역 실패");
            commented.add(number);
            comments.add(body);
        }
    }

    private TaskLedger ledger() { return new TaskLedger(temp.resolve("tasks")); }

    private WorkerRunManager manager(int timeout, String... worker) {
        return new WorkerRunManager(ledger(), task -> Files.createDirectories(temp.resolve("work").resolve(task)),
            new ProcessRunner(), (project, directory, stop, watcher) -> new Check(Check.Verdict.PASS, SUMMARY),
            new WorkerRunManager.Settings(WorkerRunManagerTest.fake(worker), Map.of(), Duration.ofSeconds(timeout)));
    }

    private IssueWatch watch(Issues issues) {
        return watch(issues, manager(60, "done", "{result}", "watch"));
    }

    private IssueWatch watch(Issues issues, WorkerRunManager manager) {
        return new IssueWatch(ledger(), manager, issues, "agent-ready", new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String printed() { return output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"); }

    @Test void repeatedPollsStartOnceAndReportAdmissionOnce() throws Exception {
        Issues issues = new Issues();
        IssueWatch watch = watch(issues);
        int[] waits = {0};
        assertThrows(InterruptedException.class, () -> watch.run(60, seconds -> {
            assertEquals(60, seconds);
            if (++waits[0] == 3) throw new InterruptedException("조회 종료");
        }));
        assertEquals(1, ledger().attempts("issue-5"));
        assertEquals(List.of(5), issues.viewed);
        assertEquals(1, issues.comments.size());
        assertEquals("issue-5: 작업자를 시작합니다.\n"
            + "issue-5: REVIEW_READY. " + SUMMARY + "\n"
            + "issue-5: 이미 접수한 이슈입니다. 상태 REVIEW_READY.\n"
            + "조회함. 새로 시작할 이슈가 없습니다.\n"
            + "조회함. 새로 시작할 이슈가 없습니다.\n", printed());
    }

    @Test void aNewManagerUsesTheSameAdmissionRecord() throws Exception {
        watch(new Issues()).poll();
        Issues next = new Issues();
        watch(next).poll();
        assertEquals(1, ledger().attempts("issue-5"));
        assertTrue(next.viewed.isEmpty());
        assertTrue(next.comments.isEmpty());
    }

    @Test void eachPollStartsOnlyTheSmallestUnadmittedNumber() throws Exception {
        Issues issues = new Issues();
        issues.listed = List.of(new IssueWatch.Issue(7, "7", "본문"), new IssueWatch.Issue(5, "5", "본문"));
        IssueWatch watch = watch(issues);
        watch.poll();
        assertEquals(List.of(5), issues.commented);
        assertFalse(Files.exists(ledger().taskDir("issue-7")));
        watch.poll();
        assertEquals(List.of(5, 7), issues.commented);
    }

    @Test void closedOrUnlabelledIssuesLeaveNoAdmissionDirectory() throws Exception {
        Issues closed = new Issues();
        closed.state = "CLOSED";
        Issues unlabelled = new Issues();
        unlabelled.labels = List.of();
        for (Issues issues : List.of(closed, unlabelled)) {
            output.reset();
            watch(issues).poll();
            assertFalse(Files.exists(ledger().taskDir("issue-5")));
            assertTrue(issues.comments.isEmpty());
            assertEquals("issue-5: 열려 있지 않거나 라벨이 없어 시작하지 않습니다.\n", printed());
        }
    }

    @Test void aFailedLookupCanStartOnTheNextPoll() throws Exception {
        Issues issues = new Issues();
        issues.failList = true;
        IssueWatch watch = watch(issues);
        watch.poll();
        assertFalse(Files.exists(ledger().taskDir("issue-5")));
        assertEquals("이슈를 조회하지 못했습니다. 다음 조회에서 다시 시도합니다. 조회 대역 실패\n", printed());
        issues.failList = false;
        watch.poll();
        assertEquals(State.REVIEW_READY, ledger().task("issue-5").state());
    }

    @Test void timedOutWorkIsNotPolledAgainButAnExplicitStartKeepsItsFiles() throws Exception {
        Issues issues = new Issues();
        IssueWatch watch = watch(issues, manager(5, "write-then-hang"));
        watch.poll();
        assertEquals("TIMED_OUT", ledger().attempt("issue-5", 1).end());
        assertEquals(State.NEEDS_CHECK, ledger().task("issue-5").state());
        assertEquals(List.of("실행 관리가 이 이슈의 작업을 실행했습니다.\n\n"
            + "- 상태: 확인 필요 (`NEEDS_CHECK`)\n- 브랜치: `task/issue-5`\n- 시도: 1"), issues.comments);
        String workspace = ledger().task("issue-5").workspace();
        watch.poll();
        assertEquals(1, ledger().attempts("issue-5"));
        assertEquals(1, issues.comments.size());
        var retried = manager(60, "done", "{result}", "retry").start("issue-5", "다시");
        assertEquals(2, retried.attempt());
        assertEquals(State.REVIEW_READY, retried.state());
        assertEquals(workspace, ledger().task("issue-5").workspace());
        assertEquals("앞선 시도의 변경", Files.readString(Path.of(workspace).resolve("kept.txt")));
    }

    @Test void passedWorkCommentsExactlyAndUsesTheRereadRequest() throws Exception {
        Issues issues = new Issues();
        watch(issues, manager(60, "hooked", "{result}", "watch", "stopped")).poll();
        assertEquals("issue-5: 작업자를 시작합니다.\nissue-5: REVIEW_READY. " + SUMMARY
            + " 종료 Hook이 작업자의 세션을 멈췄습니다: 대역 Hook 판정\n", printed());
        assertEquals(List.of("실행 관리가 이 이슈의 작업을 실행했습니다.\n\n"
            + "- 상태: 검토 대기 (`REVIEW_READY`)\n- 브랜치: `task/issue-5`\n- 시도: 1\n- 검사: 검사 83, 실패 0, 건너뜀 0."), issues.comments);
        String expected = "작업 원칙은 `AGENTS.md`, 절차는 `inquiry-change` Skill, 현재 구조는 `docs/project.md`, 결정과 이유는 `docs/adr/`에 있습니다.\n"
            + "아래 이슈가 승인된 요구입니다. 이슈가 정하지 않은 공개 동작을 스스로 정해야 하면 멈추고 질문을 돌려주세요.\n"
            + "라이브러리를 더하지 않습니다. 실제 모델을 호출하지 않습니다. 환경변수 파일과 `.local/harness/manager.json`을 읽지 않습니다. 커밋하지 않습니다.\n"
            + "마지막 응답은 지정된 결과 형식의 JSON입니다.\n\n# 현재 제목\n\n현재 본문\n둘째 줄";
        assertEquals(expected, Files.readString(ledger().attemptDir("issue-5", 1).resolve("request.md")));
    }

    @Test void aFailedCommentDoesNotChangeTheTaskState() throws Exception {
        Issues issues = new Issues();
        issues.failComment = true;
        watch(issues).poll();
        assertEquals(State.REVIEW_READY, ledger().task("issue-5").state());
        assertTrue(printed().endsWith("issue-5: 이슈에 결과를 남기지 못했습니다. 댓글 대역 실패\n"));
    }
}
