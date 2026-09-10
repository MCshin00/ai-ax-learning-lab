package lab.week05.harness;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import lab.week05.ProcessRunner;
import lab.week05.harness.DevelopmentHarness.*;
import static org.junit.jupiter.api.Assertions.*;

class GradleVerifierTest {
    @TempDir Path temp;
    private void suite(Path directory, int failures, int skipped) throws Exception {
        Files.writeString(directory.resolve("TEST-case.xml"),
            "<testsuite name=\"case\" tests=\"1\" failures=\"" + failures
            + "\" errors=\"0\" skipped=\"" + skipped + "\"></testsuite>");
    }
    @Test void shellStartupFailureIsNotARepairableTestFailure() throws Exception {
        List<String> command = System.getProperty("os.name").startsWith("Windows")
            ? List.of("cmd", "/d", "/c", "__missing_harness_verifier__")
            : List.of("sh", "-c", "exit 127");
        StepResult result = new GradleVerifier().run(command, temp, 10, temp.resolve("check"));
        assertTrue(result.exitCode() > 0);
        assertEquals(ResultKind.UNAVAILABLE, result.kind());
    }
    @Test void failedTestsNeedANewReportAndNonzeroExit() throws Exception {
        suite(temp, 1, 0);
        StepResult result = GradleVerifier.assess(new ProcessRunner.Result(1, "test failed", false), temp);
        assertEquals(ResultKind.FAILED, result.kind());
        assertEquals(1, result.exitCode());
    }
    @Test void missingContradictoryOrEntirelySkippedResultsCannotPass() throws Exception {
        assertEquals(ResultKind.UNAVAILABLE,
            GradleVerifier.assess(new ProcessRunner.Result(0, "", false), temp).kind());
        suite(temp, 1, 0);
        assertEquals(ResultKind.UNAVAILABLE,
            GradleVerifier.assess(new ProcessRunner.Result(0, "", false), temp).kind());
        suite(temp, 0, 0);
        assertEquals(ResultKind.UNAVAILABLE,
            GradleVerifier.assess(new ProcessRunner.Result(1, "", false), temp).kind());
        suite(temp, 0, 1);
        assertEquals(ResultKind.UNAVAILABLE,
            GradleVerifier.assess(new ProcessRunner.Result(0, "", false), temp).kind());
    }
    @Test void successRequiresExecutedTestsAndTimeoutRemainsAnExecutionFailure() throws Exception {
        suite(temp, 0, 0);
        assertEquals(ResultKind.OK,
            GradleVerifier.assess(new ProcessRunner.Result(0, "passed", false), temp).kind());
        assertEquals(ResultKind.TIMED_OUT,
            GradleVerifier.assess(new ProcessRunner.Result(124, "", true), temp).kind());
    }
    @Test void aPreviousReportDirectoryIsNeverReused() {
        assertThrows(FileAlreadyExistsException.class,
            () -> new GradleVerifier().run(List.of("unused"), temp, 10, temp));
    }
}
