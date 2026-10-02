package lab.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class StopHook {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern EVENT_NAME = Pattern.compile("\"hook_event_name\"\\s*:\\s*\"([^\"\\\\]*)\"");
    private static final Pattern TURN_ID = Pattern.compile("\"turn_id\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern STOP_HOOK_ACTIVE = Pattern.compile("\"stop_hook_active\"\\s*:\\s*(true|false)");

    private StopHook() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: StopHook <record-file> <project-root> [windows-socket-dir]");
        }
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Path socketDir = args.length == 3 ? Path.of(args[2]) : null;
        String response = process(input, Path.of(args[0]), Path.of(args[1]), socketDir,
            new GradleRunner(), UUID.randomUUID().toString(), Instant.now());
        System.out.print(response);
    }

    static String process(String input, Path recordFile, Path projectRoot, Path socketDir,
                          Runner runner, String runId, Instant recordedAt) throws IOException {
        String eventName = requiredValue(EVENT_NAME, input, "hook_event_name");
        if (!"Stop".equals(eventName)) throw new IllegalArgumentException("Expected a Stop event");
        String turnId = requiredValue(TURN_ID, input, "turn_id");
        boolean active = Boolean.parseBoolean(requiredValue(STOP_HOOK_ACTIVE, input, "stop_hook_active"));

        Path root = projectRoot.toAbsolutePath().normalize();
        Path runDir = root.resolve(".local/harness/runs").resolve(runId);
        Path buildDir = runDir.resolve("build");
        Path logFile = runDir.resolve("gradle.log");
        Files.createDirectories(runDir);
        RunResult run = runner.run(root, buildDir, logFile, socketDir, TEST_TIMEOUT);
        Verdict verdict = judge(run, buildDir, root, logFile);

        String record = "{\"recorded_at\":\"" + recordedAt + "\","
            + "\"hook_event_name\":\"Stop\","
            + "\"turn_id\":\"" + turnId + "\","
            + "\"stop_hook_active\":" + active + ","
            + "\"check_status\":\"" + verdict.status().name().toLowerCase() + "\","
            + "\"summary\":\"" + jsonEscape(verdict.summary()) + "\","
            + "\"result_file\":\"" + jsonEscape(verdict.resultFile()) + "\"}\n";
        Path target = recordFile.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, record, StandardCharsets.UTF_8);
        return responseFor(verdict);
    }

    private static Verdict judge(RunResult run, Path buildDir, Path root, Path logFile) {
        String log = root.relativize(logFile).toString();
        if (run.timedOut()) return new Verdict(Status.UNAVAILABLE, "업무 검사가 120초 안에 끝나지 않았습니다", log);
        if (run.error() != null) return new Verdict(Status.UNAVAILABLE, run.error(), log);

        Path reportDir = buildDir.resolve("test-results/acceptance");
        try {
            if (!Files.isDirectory(reportDir)) return new Verdict(Status.UNAVAILABLE, "새 업무 검사 결과 파일이 없습니다", log);
            List<Path> reports;
            try (Stream<Path> files = Files.list(reportDir)) {
                reports = files.filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml"))
                    .sorted().toList();
            }
            if (reports.isEmpty()) return new Verdict(Status.UNAVAILABLE, "새 업무 검사 결과 파일이 없습니다", log);

            int tests = 0;
            int skipped = 0;
            int failures = 0;
            String firstFailure = null;
            String firstReport = null;
            for (Path report : reports) {
                Element suite = readSuite(report);
                tests += count(suite, "tests");
                skipped += count(suite, "skipped");
                failures += count(suite, "failures") + count(suite, "errors");
                if (firstFailure == null) {
                    firstFailure = firstFailure(suite);
                    if (firstFailure != null) firstReport = root.relativize(report).toString();
                }
            }
            if (tests == 0 || tests == skipped) {
                return new Verdict(Status.UNAVAILABLE, "실행된 업무 검사가 없습니다", log);
            }
            if (failures > 0) {
                return new Verdict(Status.FAILURE,
                    firstFailure == null ? "업무 검사에서 실패가 보고됐습니다" : firstFailure,
                    firstReport == null ? root.relativize(reports.get(0)).toString() : firstReport);
            }
            if (run.exitCode() != 0) {
                return new Verdict(Status.UNAVAILABLE, "업무 검사는 통과했으나 Gradle 종료 코드가 " + run.exitCode() + "입니다", log);
            }
            return new Verdict(Status.PASS, "업무 검사 통과", root.relativize(reports.get(0)).toString());
        } catch (Exception e) {
            return new Verdict(Status.UNAVAILABLE, "업무 검사 결과를 읽을 수 없습니다: " + e.getMessage(), log);
        }
    }

    private static Element readSuite(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element suite = factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
        if (!"testsuite".equals(suite.getTagName())) throw new IllegalArgumentException("Unexpected XML root");
        return suite;
    }

    private static int count(Element suite, String name) {
        return Integer.parseInt(suite.getAttribute(name));
    }

    private static String firstFailure(Element suite) {
        for (Node test = suite.getFirstChild(); test != null; test = test.getNextSibling()) {
            if (!(test instanceof Element testCase) || !"testcase".equals(testCase.getTagName())) continue;
            for (Node child = testCase.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element detail)) continue;
                if (!"failure".equals(detail.getTagName()) && !"error".equals(detail.getTagName())) continue;
                String message = detail.getAttribute("message");
                if (message.isBlank()) message = detail.getTextContent();
                message = message.replace("\r", "\\r").replace("\n", "\\n");
                if (message.length() > 700) message = message.substring(0, 700) + "...";
                return testCase.getAttribute("name") + ": " + message;
            }
        }
        return null;
    }

    private static String responseFor(Verdict verdict) {
        if (verdict.status() == Status.PASS) return "{}";
        String kind = verdict.status() == Status.FAILURE ? "CSV 업무 검사 실패" : "CSV 업무 검사 실행 불가";
        String message = kind + ": " + verdict.summary() + ". 결과: " + verdict.resultFile();
        return "{\"continue\":false,\"stopReason\":\"" + jsonEscape(kind)
            + "\",\"systemMessage\":\"" + jsonEscape(message) + "\"}";
    }

    private static String requiredValue(Pattern pattern, String input, String field) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) throw new IllegalArgumentException("Missing or invalid " + field);
        return matcher.group(1);
    }

    private static String jsonEscape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    enum Status { PASS, FAILURE, UNAVAILABLE }
    record Verdict(Status status, String summary, String resultFile) {}
    record RunResult(int exitCode, boolean timedOut, String error) {}

    interface Runner {
        RunResult run(Path projectRoot, Path buildDir, Path logFile, Path socketDir, Duration timeout);
    }

    static final class GradleRunner implements Runner {
        @Override public RunResult run(Path projectRoot, Path buildDir, Path logFile, Path socketDir, Duration timeout) {
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            Path wrapper = projectRoot.resolve(windows ? "gradlew.bat" : "gradlew");
            if (!Files.isRegularFile(wrapper)) return new RunResult(-1, false, "Gradle wrapper가 없습니다");
            try {
                Files.createDirectories(logFile.getParent());
                ProcessBuilder command = new ProcessBuilder(wrapper.toString(), "acceptance", "--rerun-tasks",
                    "-PcourseBuildDir=" + buildDir);
                command.directory(projectRoot.toFile());
                command.redirectErrorStream(true);
                command.redirectOutput(logFile.toFile());
                if (windows && socketDir != null) {
                    Path expected = projectRoot.resolve(".local/sock");
                    Files.createDirectories(expected);
                    if (!socketDir.toRealPath().equals(expected.toRealPath())) {
                        return new RunResult(-1, false, "소켓 경로가 프로젝트의 .local/sock을 가리키지 않습니다");
                    }
                    String option = "-Djdk.net.unixdomain.tmpdir=\"" + socketDir + "\"";
                    command.environment().merge("JAVA_TOOL_OPTIONS", option, (oldValue, added) -> oldValue + " " + added);
                }
                Process process = command.start();
                try {
                    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        stopProcess(process);
                        return new RunResult(-1, true, null);
                    }
                    return new RunResult(process.exitValue(), false, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stopProcess(process);
                    return new RunResult(-1, false, "검사 대기가 중단됐습니다");
                }
            } catch (IOException e) {
                return new RunResult(-1, false, "Gradle을 시작할 수 없습니다: " + e.getMessage());
            }
        }

        private static void stopProcess(Process process) {
            List<ProcessHandle> descendants = process.descendants().toList();
            descendants.forEach(ProcessHandle::destroy);
            process.destroy();
            descendants.forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
