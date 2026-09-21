package lab.week05.harness;

import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lab.week05.ProcessRunner;
import lab.week05.harness.DevelopmentHarness.*;

/** 새 JUnit 근거가 있는 검사 실패와 검사 실행 자체의 실패를 구분한다. */
public final class GradleVerifier {
    public StepResult run(List<String> command, Path workspace, int timeoutSeconds, Path reports) throws Exception {
        List<String> groups = reportGroups(command);
        Files.createDirectory(reports);
        List<String> actual = new ArrayList<>(command);
        actual.addAll(List.of("--rerun-tasks", "--no-build-cache",
            "-PharnessReportDir=" + reports.toAbsolutePath().normalize()));
        ProcessRunner.Result result = new ProcessRunner().run(actual, workspace, "", timeoutSeconds);
        StepResult assessment = assess(result, reports, groups);
        Files.writeString(reports.resolve("verification.txt"), assessment.kind() + " (종료 코드 "
            + assessment.exitCode() + ")\n" + assessment.output());
        return assessment;
    }

    // 명령 배열의 Gradle 속성으로 결과 그룹을 지정한다. 생략하면 기존 단일 검사 위치를 쓴다.
    static List<String> reportGroups(List<String> command) {
        String prefix = "-PharnessReportTasks=";
        List<String> values = command.stream().filter(arg -> arg.startsWith(prefix)).toList();
        if (values.isEmpty()) return List.of("");
        if (values.size() != 1) throw new IllegalArgumentException("검사 결과 그룹은 한 번만 지정하세요.");
        List<String> groups = List.of(values.get(0).substring(prefix.length()).split(",", -1));
        if (groups.stream().anyMatch(group -> !group.matches("[A-Za-z][A-Za-z0-9_-]*"))
                || new HashSet<>(groups).size() != groups.size()) {
            throw new IllegalArgumentException("검사 결과 그룹은 중복 없는 단순 작업 이름이어야 합니다.");
        }
        return groups;
    }

    static StepResult assess(ProcessRunner.Result result, Path reports) {
        return assess(result, reports, List.of(""));
    }

    static StepResult assess(ProcessRunner.Result result, Path reports, List<String> groups) {
        if (result.timedOut()) return StepResult.fromProcess(result);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            long tests = 0, failures = 0, errors = 0, skipped = 0;
            boolean complete = !groups.isEmpty();
            StringBuilder detail = new StringBuilder();
            for (String group : groups) {
                Path directory = reports.resolve(group);
                long groupTests = 0, groupSkipped = 0;
                List<Path> files = List.of();
                if (Files.isDirectory(directory)) {
                    try (var entries = Files.list(directory)) {
                        files = entries.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith("TEST-")
                                && path.getFileName().toString().endsWith(".xml")).toList();
                    }
                }
                for (Path file : files) {
                    var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                    if (!suite.getTagName().equals("testsuite")) throw new IllegalArgumentException("JUnit 형식을 확인할 수 없습니다.");
                    long t = count(suite, "tests"), f = count(suite, "failures");
                    long e = count(suite, "errors"), s = count(suite, "skipped");
                    if (f + e + s > t) throw new IllegalArgumentException("JUnit 검사 수가 일치하지 않습니다.");
                    groupTests += t; groupSkipped += s;
                    tests += t; failures += f; errors += e; skipped += s;
                }
                boolean executed = groupTests > groupSkipped;
                complete &= executed;
                detail.append("\n결과 그룹 ").append(group.isEmpty() ? "test (기존 경로)" : group)
                    .append(executed ? ": 실행 근거 있음." : ": 실행 근거 부족.");
            }
            ResultKind kind = ResultKind.UNAVAILABLE;
            if (complete) {
                if (result.exitCode() == 0 && failures + errors == 0) kind = ResultKind.OK;
                else if (result.exitCode() > 0 && failures + errors > 0) kind = ResultKind.FAILED;
            }
            String summary = "이번 JUnit 결과: 검사 " + tests + ", 실패 " + failures
                + ", 오류 " + errors + ", 건너뜀 " + skipped + "." + detail;
            if (kind == ResultKind.UNAVAILABLE) summary += " 검사 실행 근거가 없거나 종료 코드와 일치하지 않습니다.";
            return new StepResult(kind, result.exitCode(), result.output() + "\n" + summary);
        } catch (Exception failure) {
            return new StepResult(ResultKind.UNAVAILABLE, result.exitCode(),
                result.output() + "\n새 검사 결과를 읽을 수 없습니다: " + failure.getMessage());
        }
    }
    private static long count(org.w3c.dom.Element suite, String name) {
        long value = Long.parseLong(suite.getAttribute(name));
        if (value < 0) throw new IllegalArgumentException("음수 검사 수는 사용할 수 없습니다.");
        return value;
    }
}
