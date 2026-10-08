package lab.harness.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 작업자의 보고와 별개로 검사를 실행해 통과·실패·실행 불가를 가른다. 5주차의 GradleVerifier에서 가져왔다.
 * 이번 실행이 새로 만든 결과 파일만 읽는 판정과, 검사 작업마다 실행 근거를 확인하는 것은 그대로다.
 * 결과 폴더를 넘기는 방식을 이 프로젝트의 courseBuildDir로 바꿨고, 첫 실패의 이름과 메시지를 요약에 넣으며,
 * 검사 프로세스의 중단과 종료 확인을 작업자 실행과 같은 방식으로 다룬다.
 */
public final class GradleChecker implements Checker {
    private final List<String> command;
    private final Map<String, String> environment;
    private final Duration timeout;
    private final List<String> resultGroups;

    /**
     * command의 {project}는 검사할 프로젝트 폴더로, {build}는 이번 검사의 빌드 폴더로 바뀐다.
     * resultGroups는 결과를 읽을 검사 작업 이름이다. 명령이 실행하는 검사 작업과 같아야 한다.
     */
    public GradleChecker(List<String> command, Map<String, String> environment, Duration timeout, List<String> resultGroups) {
        if (resultGroups.isEmpty() || resultGroups.stream().anyMatch(group -> !group.matches("[A-Za-z][A-Za-z0-9_-]*"))) {
            throw new IllegalArgumentException("결과를 읽을 검사 작업 이름이 필요합니다.");
        }
        if (command.stream().noneMatch(part -> part.contains("{build}"))) {
            throw new IllegalArgumentException("검사 명령에 이번 검사의 빌드 폴더 {build}가 들어가야 합니다.");
        }
        this.command = List.copyOf(command);
        this.environment = Map.copyOf(environment);
        this.timeout = timeout;
        this.resultGroups = List.copyOf(resultGroups);
    }

    @Override public Check run(Path project, Path checkDir, ProcessRunner.StopSignal stop, ProcessRunner.Watcher watcher) {
        Path build = checkDir.resolve("build");
        try {
            // 이미 있는 폴더는 쓰지 않는다. 앞선 실행의 결과를 이번 결과로 읽지 않기 위해서다.
            Files.createDirectories(checkDir.getParent());
            Files.createDirectory(checkDir);
        } catch (IOException failure) {
            return new Check(Check.Verdict.UNAVAILABLE, "검사 결과 폴더를 새로 만들지 못했습니다: " + failure.getMessage());
        }
        List<String> actual = command.stream().map(part -> part
            .replace("{project}", project.toString()).replace("{build}", build.toString())).toList();
        ProcessRunner.Result result = new ProcessRunner().run(actual, project, environment, "",
            checkDir.resolve("gradle.log"), timeout, stop, watcher);
        return assess(result, build.resolve("test-results"), resultGroups);
    }

    static Check assess(ProcessRunner.Result result, Path reports, List<String> groups) {
        boolean confirmed = result.terminationConfirmed();
        if (!confirmed) return unavailable("검사 프로세스의 종료가 확인되지 않았습니다.", false);
        if (result.end() == ProcessRunner.End.TIMED_OUT) return unavailable("검사가 제한 시간 안에 끝나지 않았습니다.", true);
        if (result.end() == ProcessRunner.End.INTERRUPTED) return unavailable("중단 요청으로 검사를 끝냈습니다.", true);
        if (result.end() != ProcessRunner.End.EXITED) return unavailable("검사를 실행하지 못했습니다. " + result.note(), true);
        try {
            long tests = 0, failed = 0, skipped = 0;
            String firstFailure = null;
            String missing = null;
            for (String group : groups) {
                Path directory = reports.resolve(group);
                long groupTests = 0, groupSkipped = 0;
                if (Files.isDirectory(directory)) {
                    List<Path> files;
                    try (var entries = Files.list(directory)) {
                        files = entries.filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
                    }
                    for (Path file : files) {
                        Element suite = suite(file);
                        groupTests += count(suite, "tests");
                        groupSkipped += count(suite, "skipped");
                        failed += count(suite, "failures") + count(suite, "errors");
                        if (firstFailure == null) firstFailure = firstFailure(suite);
                    }
                }
                tests += groupTests;
                skipped += groupSkipped;
                if (groupTests == groupSkipped && missing == null) missing = group;
            }
            String counts = "검사 " + tests + ", 실패 " + failed + ", 건너뜀 " + skipped + ".";
            // 실패가 있으면 다른 검사 작업이 실행되지 않았더라도 실패다. Gradle은 첫 실패에서 뒤의 작업을 멈춘다.
            if (failed > 0) return new Check(Check.Verdict.FAILED, counts + (firstFailure == null ? "" : " 첫 실패: " + firstFailure));
            if (missing != null) return unavailable("검사 작업 " + missing + "의 실행된 검사가 없습니다. " + counts, true);
            if (result.exitCode() != 0) return unavailable("검사는 실패가 없는데 종료 코드가 " + result.exitCode() + "입니다. " + counts, true);
            return new Check(Check.Verdict.PASS, counts);
        } catch (Exception failure) {
            return unavailable("검사 결과를 읽을 수 없습니다: " + failure.getMessage(), true);
        }
    }

    private static Check unavailable(String summary, boolean confirmed) {
        return new Check(Check.Verdict.UNAVAILABLE, summary, confirmed);
    }

    private static Element suite(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        if (!"testsuite".equals(suite.getTagName())) throw new IllegalArgumentException("JUnit 형식이 아닙니다.");
        return suite;
    }

    private static long count(Element suite, String name) {
        return Long.parseLong(suite.getAttribute(name));
    }

    private static String firstFailure(Element suite) {
        for (Node test = suite.getFirstChild(); test != null; test = test.getNextSibling()) {
            if (!(test instanceof Element testCase) || !"testcase".equals(testCase.getTagName())) continue;
            for (Node child = testCase.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element detail)) continue;
                if (!"failure".equals(detail.getTagName()) && !"error".equals(detail.getTagName())) continue;
                String message = detail.getAttribute("message").replace("\r", " ").replace("\n", " ");
                return testCase.getAttribute("name") + ": " + (message.length() > 500 ? message.substring(0, 500) + "..." : message);
            }
        }
        return null;
    }
}
