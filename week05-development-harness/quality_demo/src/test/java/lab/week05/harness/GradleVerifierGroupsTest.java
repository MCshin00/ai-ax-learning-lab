package lab.week05.harness;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import lab.week05.ProcessRunner;
import lab.week05.harness.DevelopmentHarness.*;
import static org.junit.jupiter.api.Assertions.*;

class GradleVerifierGroupsTest {
    @TempDir Path temp;
    private static final List<String> BOTH = List.of("test", "acceptance");
    private void suite(String group, int failures, int skipped) throws Exception {
        Path directory = Files.createDirectories(temp.resolve(group));
        Files.writeString(directory.resolve("TEST-case.xml"),
            "<testsuite tests=\"1\" failures=\"" + failures + "\" errors=\"0\" skipped=\"" + skipped + "\"/>");
    }
    private ResultKind assess(int exit) {
        return GradleVerifier.assess(new ProcessRunner.Result(exit, "", false), temp, BOTH).kind();
    }
    @Test void bothGroupsMustExecuteBeforeSuccessOrRepair() throws Exception {
        suite("test", 0, 0);
        assertEquals(ResultKind.UNAVAILABLE, assess(0));
        suite("test", 1, 0);
        assertEquals(ResultKind.UNAVAILABLE, assess(1));
        suite("test", 0, 0);
        suite("acceptance", 0, 1);
        assertEquals(ResultKind.UNAVAILABLE, assess(0));
        suite("acceptance", 0, 0);
        assertEquals(ResultKind.OK, assess(0));
        assertEquals(ResultKind.UNAVAILABLE, assess(1));
    }
    @Test void eitherGroupsFailureIsRepairableOnlyWithNonzeroExit() throws Exception {
        suite("test", 0, 0);
        suite("acceptance", 1, 0);
        assertEquals(ResultKind.FAILED, assess(1));
        assertEquals(ResultKind.UNAVAILABLE, assess(0));
        suite("test", 1, 0);
        suite("acceptance", 0, 0);
        assertEquals(ResultKind.FAILED, assess(1));
    }
    @Test void timeoutAndMalformedEvidenceDoNotTriggerRepair() throws Exception {
        suite("test", 0, 0);
        suite("acceptance", 1, 0);
        assertEquals(ResultKind.TIMED_OUT, GradleVerifier.assess(
            new ProcessRunner.Result(124, "", true), temp, BOTH).kind());
        suite("acceptance", 1, 1);
        assertEquals(ResultKind.UNAVAILABLE, assess(1));
    }
    @Test void explicitGroupsPreserveLegacyDefaultAndRejectAmbiguousPaths() {
        assertEquals(List.of(""), GradleVerifier.reportGroups(List.of("test")));
        assertEquals(BOTH, GradleVerifier.reportGroups(List.of("test", "acceptance", "-PharnessReportTasks=test,acceptance")));
        for (String value : List.of("", "test,", "../test", "test,test")) {
            assertThrows(IllegalArgumentException.class,
                () -> GradleVerifier.reportGroups(List.of("-PharnessReportTasks=" + value)));
        }
        assertThrows(IllegalArgumentException.class, () -> GradleVerifier.reportGroups(
            List.of("-PharnessReportTasks=test", "-PharnessReportTasks=acceptance")));
    }
}
