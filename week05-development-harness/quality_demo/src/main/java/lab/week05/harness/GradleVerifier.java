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
        Files.createDirectory(reports);
        List<String> actual = new ArrayList<>(command);
        actual.addAll(List.of("--rerun-tasks", "--no-build-cache",
            "-PharnessReportDir=" + reports.toAbsolutePath().normalize()));
        ProcessRunner.Result result = new ProcessRunner().run(actual, workspace, "", timeoutSeconds);
        return assess(result, reports);
    }

    static StepResult assess(ProcessRunner.Result result, Path reports) {
        if (result.timedOut()) return StepResult.fromProcess(result);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            long tests = 0, failures = 0, errors = 0, skipped = 0;
            List<Path> files;
            try (var entries = Files.list(reports)) {
                files = entries.filter(path -> path.getFileName().toString().startsWith("TEST-")
                    && path.getFileName().toString().endsWith(".xml")).toList();
            }
            for (Path file : files) {
                var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                if (!suite.getTagName().equals("testsuite")) throw new IllegalArgumentException("JUnit 형식을 확인할 수 없습니다.");
                tests += count(suite, "tests");
                failures += count(suite, "failures");
                errors += count(suite, "errors");
                skipped += count(suite, "skipped");
            }
            ResultKind kind = ResultKind.UNAVAILABLE;
            if (tests > skipped && skipped >= 0) {
                if (result.exitCode() == 0 && failures + errors == 0) kind = ResultKind.OK;
                else if (result.exitCode() > 0 && failures + errors > 0) kind = ResultKind.FAILED;
            }
            String summary = "이번 JUnit 결과: 검사 " + tests + ", 실패 " + failures
                + ", 오류 " + errors + ", 건너뜀 " + skipped + ".";
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
