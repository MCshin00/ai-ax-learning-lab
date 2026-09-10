package lab.week05;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {
    @TempDir Path temp;
    @Test void timeoutIncludesInputThatTheChildNeverReads() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String classpath = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        long started = System.nanoTime();
        var result = new ProcessRunner().run(List.of(java, "-cp", classpath, NonReadingChild.class.getName()),
            temp, "x".repeat(2_000_000), 1);
        assertTrue(result.timedOut()); assertEquals(124, result.exitCode());
        assertTrue((System.nanoTime() - started) / 1_000_000_000.0 < 8);
    }
    public static class NonReadingChild {
        public static void main(String[] args) throws Exception { Thread.sleep(30_000); }
    }
}
