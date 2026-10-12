package lab.harness.run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubIssuesTest {
    @Test void commandsAndJsonAreCheckedWithoutStartingGh() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        List<String> replies = List.of("[{\"number\":5,\"title\":\"제목\",\"body\":\"본문\"}]",
            "{\"number\":5,\"title\":\"현재 제목\",\"body\":\"현재 본문\",\"state\":\"OPEN\",\"labels\":[{\"name\":\"agent-ready\",\"color\":\"ffffff\"}]}", "");
        GitHubIssues issues = new GitHubIssues("owner/repo", (command, input) -> {
            commands.add(command);
            inputs.add(input);
            return new GitHubIssues.Reply(0, replies.get(commands.size() - 1), "표준 오류 안내");
        });
        assertEquals(List.of(new IssueWatch.Issue(5, "제목", "본문")), issues.list("agent-ready"));
        assertEquals(new IssueWatch.Detail(5, "현재 제목", "현재 본문", "OPEN", List.of(new IssueWatch.Label("agent-ready"))), issues.view(5));
        issues.comment(5, "댓글\n둘째 줄");
        assertEquals(List.of(
            List.of("gh", "issue", "list", "--repo", "owner/repo", "--state", "open", "--label", "agent-ready", "--json", "number,title,body", "--limit", "20"),
            List.of("gh", "issue", "view", "5", "--repo", "owner/repo", "--json", "number,title,body,state,labels"),
            List.of("gh", "issue", "comment", "5", "--repo", "owner/repo", "--body-file", "-")), commands);
        assertEquals(List.of("", "", "댓글\n둘째 줄"), inputs);
    }

    @Test void failedLookupValuesAreNotAnEmptyList() throws Exception {
        for (GitHubIssues.Command command : List.<GitHubIssues.Command>of(
                (args, input) -> { throw new IOException("시작 실패"); },
                (args, input) -> new GitHubIssues.Reply(1, "조회 실패", ""),
                (args, input) -> new GitHubIssues.Reply(0, "{}", ""))) {
            assertThrows(IOException.class, () -> new GitHubIssues("owner/repo", command).list("agent-ready"));
        }
        IOException error = assertThrows(IOException.class, () -> new GitHubIssues("owner/repo",
            (args, input) -> new GitHubIssues.Reply(1, "표준 출력", "조회 실패\n")).list("agent-ready"));
        assertEquals("gh 종료 코드 1: 조회 실패", error.getMessage());
        IOException fallback = assertThrows(IOException.class, () -> new GitHubIssues("owner/repo",
            (args, input) -> new GitHubIssues.Reply(1, "조회 실패\n", "")).list("agent-ready"));
        assertEquals("gh 종료 코드 1: 조회 실패", fallback.getMessage());
        assertEquals(List.of(), new GitHubIssues("owner/repo", (args, input) -> new GitHubIssues.Reply(0, "[]", "")).list("agent-ready"));
    }
}
