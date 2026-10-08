package lab.harness.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lab.harness.run.Checker.Check;
import lab.harness.run.ProcessRunner.End;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GradleCheckerTest {
    private static final List<String> TEST = List.of("test");

    @TempDir Path temp;

    private static ProcessRunner.Result ended(End end, int exitCode) {
        return new ProcessRunner.Result(end, exitCode, true, true, List.of(), "");
    }

    private Path reports() {
        return temp.resolve("test-results");
    }

    private void report(String group, String attributes, String body) throws Exception {
        Path directory = Files.createDirectories(reports().resolve(group));
        Files.writeString(directory.resolve("TEST-a.xml"), "<testsuite " + attributes + ">" + body + "</testsuite>");
    }

    // 아래 네 검사는 준비한 결과 파일에 대한 판정만 본다. 결과가 이번 실행의 것인지는 그 다음 검사들이 본다.

    @Test void passingResultsAreAPass() throws Exception {
        report("test", "tests=\"3\" skipped=\"0\" failures=\"0\" errors=\"0\"", "");
        assertEquals(Check.Verdict.PASS, GradleChecker.assess(ended(End.EXITED, 0), reports(), TEST).verdict());
    }

    @Test void aFailingTestIsAFailureWithItsNameAndMessage() throws Exception {
        report("test", "tests=\"2\" skipped=\"0\" failures=\"1\" errors=\"0\"",
            "<testcase name=\"absentServiceIsNotAnError()\"><failure message=\"없음이어야 하는데 오류\"/></testcase>");
        Check check = GradleChecker.assess(ended(End.EXITED, 1), reports(), TEST);
        assertEquals(Check.Verdict.FAILED, check.verdict());
        assertTrue(check.summary().contains("absentServiceIsNotAnError"));
        assertTrue(check.summary().contains("없음이어야 하는데 오류"));
    }

    @Test void withoutExecutedTestsTheCheckIsUnavailable() throws Exception {
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.EXITED, 1), reports(), TEST).verdict());
        report("test", "tests=\"2\" skipped=\"2\" failures=\"0\" errors=\"0\"", "");
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.EXITED, 0), reports(), TEST).verdict());
    }

    @Test void aTimeoutABuildErrorOrAnUnconfirmedEndIsUnavailableEvenWithPassingResults() throws Exception {
        report("test", "tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\"", "");
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.TIMED_OUT, -1), reports(), TEST).verdict());
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.START_FAILED, -1), reports(), TEST).verdict());
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.EXITED, 1), reports(), TEST).verdict());

        Check left = GradleChecker.assess(new ProcessRunner.Result(End.EXITED, 0, true, false, List.of(), ""), reports(), TEST);
        assertEquals(Check.Verdict.UNAVAILABLE, left.verdict());
        assertFalse(left.terminationConfirmed());
    }

    @Test void everyNamedCheckTaskNeedsItsOwnResults() throws Exception {
        List<String> both = List.of("test", "harnessTest");
        report("test", "tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\"", "");
        // 한 검사 작업의 결과만 있으면 통과가 아니다.
        assertEquals(Check.Verdict.UNAVAILABLE, GradleChecker.assess(ended(End.EXITED, 0), reports(), both).verdict());
        // 다른 검사 작업의 실패는 실패로 읽는다.
        report("harnessTest", "tests=\"1\" skipped=\"0\" failures=\"1\" errors=\"0\"",
            "<testcase name=\"hookJudgesFreshResults()\"><failure message=\"실패\"/></testcase>");
        Check check = GradleChecker.assess(ended(End.EXITED, 1), reports(), both);
        assertEquals(Check.Verdict.FAILED, check.verdict());
        assertTrue(check.summary().contains("hookJudgesFreshResults"));
    }

    // 대역 검사 명령은 written에 적힌 검사 작업의 결과만 쓴다. 판정은 항상 test의 결과를 읽는다.
    private GradleChecker checker(String verdict, String... written) {
        List<String> command = new ArrayList<>(WorkerRunManagerTest.fake("check", "{build}", verdict));
        command.addAll(List.of(written));
        return new GradleChecker(command, Map.of(), Duration.ofSeconds(60), TEST);
    }

    @Test void theCheckReadsOnlyWhatThisRunWroteIntoItsOwnFolder() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        // 프로젝트의 평소 결과 폴더에는 통과한 옛 결과가 있다.
        Path old = Files.createDirectories(project.resolve("build/test-results/test"));
        Files.writeString(old.resolve("TEST-old.xml"), "<testsuite tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\"/>");

        Path first = temp.resolve("attempt-1/check");
        Check failed = checker("fail", "test").run(project, first, () -> false, processes -> { });
        assertEquals(Check.Verdict.FAILED, failed.verdict());
        assertTrue(Files.exists(first.resolve("build/test-results/test/TEST-fake.xml")));
        assertTrue(Files.exists(first.resolve("gradle.log")));

        // 결과를 쓰지 않은 실행은 옛 결과가 있어도 통과가 아니다.
        Check nothing = checker("pass").run(project, temp.resolve("attempt-2/check"), () -> false, processes -> { });
        assertEquals(Check.Verdict.UNAVAILABLE, nothing.verdict());

        // 이미 있는 검사 폴더는 다시 쓰지 않는다.
        Check reused = checker("pass", "test").run(project, first, () -> false, processes -> { });
        assertEquals(Check.Verdict.UNAVAILABLE, reused.verdict());
        assertEquals(Check.Verdict.PASS, checker("pass", "test").run(project, temp.resolve("attempt-3/check"), () -> false, processes -> { }).verdict());
    }

    @Test void theCommandMustNameTheBuildFolderOfThisRun() {
        assertThrows(IllegalArgumentException.class,
            () -> new GradleChecker(List.of("gradlew", "test"), Map.of(), Duration.ofSeconds(1), TEST));
        assertThrows(IllegalArgumentException.class,
            () -> new GradleChecker(List.of("gradlew", "-PcourseBuildDir={build}"), Map.of(), Duration.ofSeconds(1), List.of()));
    }
}
