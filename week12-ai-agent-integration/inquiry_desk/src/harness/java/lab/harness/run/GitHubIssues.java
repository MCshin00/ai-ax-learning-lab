package lab.harness.run;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class GitHubIssues implements IssueWatch.Issues {
    record Reply(int exitCode, String output, String error) {}
    @FunctionalInterface interface Command {
        Reply run(List<String> command, String input) throws IOException, InterruptedException;
    }

    private static final ObjectMapper JSON = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String repository;
    private final Command command;

    GitHubIssues(String repository) { this(repository, GitHubIssues::execute); }

    GitHubIssues(String repository, Command command) {
        this.repository = repository;
        this.command = command;
    }

    @Override public List<IssueWatch.Issue> list(String label) throws IOException, InterruptedException {
        String output = call(List.of("gh", "issue", "list", "--repo", repository, "--state", "open",
            "--label", label, "--json", "number,title,body", "--limit", "20"), "");
        var array = JSON.readTree(output);
        if (array == null || !array.isArray()) throw new IOException("이슈 목록이 JSON 배열이 아닙니다.");
        return List.of(JSON.treeToValue(array, IssueWatch.Issue[].class));
    }

    @Override public IssueWatch.Detail view(int number) throws IOException, InterruptedException {
        return JSON.readValue(call(List.of("gh", "issue", "view", Integer.toString(number), "--repo", repository,
            "--json", "number,title,body,state,labels"), ""), IssueWatch.Detail.class);
    }

    @Override public void comment(int number, String body) throws IOException, InterruptedException {
        call(List.of("gh", "issue", "comment", Integer.toString(number), "--repo", repository, "--body-file", "-"), body);
    }

    private String call(List<String> arguments, String input) throws IOException, InterruptedException {
        Reply reply = command.run(arguments, input);
        if (reply.exitCode() != 0) throw new IOException("gh 종료 코드 " + reply.exitCode() + ": "
            + (reply.error().isBlank() ? reply.output() : reply.error()).strip());
        return reply.output();
    }

    private static Reply execute(List<String> command, String input) throws IOException, InterruptedException {
        Path log = Files.createTempFile("issue-gh-", ".log");
        try {
            Path error = Files.createTempFile("issue-gh-error-", ".log");
            try {
                Process process = new ProcessBuilder(command).redirectOutput(log.toFile()).redirectError(error.toFile()).start();
                try (var stdin = process.outputWriter(StandardCharsets.UTF_8)) { stdin.write(input); }
                int exit = process.waitFor();
                return new Reply(exit, Files.readString(log, StandardCharsets.UTF_8), Files.readString(error, StandardCharsets.UTF_8));
            } finally {
                Files.deleteIfExists(error);
            }
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
