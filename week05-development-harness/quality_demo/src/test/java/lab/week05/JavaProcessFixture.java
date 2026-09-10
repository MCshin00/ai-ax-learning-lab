package lab.week05;

import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaProcessFixture {
    private JavaProcessFixture() {}

    static Execution execute(Class<?> mainClass, Path temporaryDirectory, String... arguments) throws Exception {
        String classpath = Path.of(mainClass.getProtectionDomain().getCodeSource().getLocation().toURI())
            + File.pathSeparator + Path.of(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-cp", classpath, mainClass.getName()));
        command.addAll(List.of(arguments));
        Path stdout = Files.createTempFile(temporaryDirectory, "stdout-", ".txt");
        Path stderr = Files.createTempFile(temporaryDirectory, "stderr-", ".txt");
        Process process = new ProcessBuilder(command)
            .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Java 실행 제한 시간 초과");
            return new Execution(process.exitValue(), Files.readString(stdout, StandardCharsets.UTF_8),
                Files.readString(stderr, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) { process.destroyForcibly().waitFor(10, TimeUnit.SECONDS); }
        }
    }

    record Execution(int exitCode, String stdout, String stderr) {}
}
