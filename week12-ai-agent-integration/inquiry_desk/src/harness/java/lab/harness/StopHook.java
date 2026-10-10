package lab.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class StopHook {
    static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);

    private StopHook() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: StopHook <record-file> <project-root> [windows-socket-dir]");
        }
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Path socketDir = args.length == 3 ? Path.of(args[2]) : null;
        String response = process(input, Path.of(args[0]), Path.of(args[1]), socketDir,
            new GradleRunner(), UUID.randomUUID().toString(), Instant.now());
        // 표준 출력의 기본 문자 집합이 UTF-8이 아닌 환경에서도 한글이 깨지지 않게 바이트로 쓴다.
        System.out.write(response.getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }

    static String process(String input, Path recordFile, Path projectRoot, Path socketDir,
                          Runner runner, String runId, Instant recordedAt) throws IOException {
        if (!(Json.parse(input) instanceof Map<?, ?> event)) throw new IllegalArgumentException("Expected a JSON object");
        if (!"Stop".equals(event.get("hook_event_name"))) throw new IllegalArgumentException("Expected a Stop event");
        if (!(event.get("turn_id") instanceof String turnId)) throw new IllegalArgumentException("Missing or invalid turn_id");
        if (!(event.get("stop_hook_active") instanceof Boolean active)) {
            throw new IllegalArgumentException("Missing or invalid stop_hook_active");
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        if (stoppedWithQuestions(event.get("last_assistant_message"))) {
            // 질문을 남기고 멈춘 작업은 검사하지 않는다. 멈춘 코드의 실패를 고칠 실패로 읽으면 안 된다.
            record(recordFile, recordedAt, turnId, active, "question", "skipped", "질문을 남기고 멈춘 작업이라 검사하지 않았습니다", "");
            return "{}";
        }
        Path runDir = root.resolve(".local/harness/runs").resolve(runId);
        Path buildDir = runDir.resolve("build");
        Path logFile = runDir.resolve("gradle.log");
        Files.createDirectories(runDir);
        RunResult run = runner.run(root, buildDir, logFile, socketDir, TEST_TIMEOUT);
        Verdict verdict = judge(run, buildDir, root, logFile);

        record(recordFile, recordedAt, turnId, active, verdict.status().name().toLowerCase(),
            actionFor(verdict, active), verdict.summary(), verdict.resultFile());
        return responseFor(verdict, active);
    }

    /**
     * 작업자의 마지막 응답이 결과 JSON이고, status가 stopped이며, 질문이 하나 이상일 때만 "질문을 남기고 멈춤"이다.
     * 글로 쓴 질문이나 질문이 빈 멈춤 결과는 여기에 들지 않아 검사를 실행한다.
     */
    static boolean stoppedWithQuestions(Object lastMessage) {
        if (!(lastMessage instanceof String message)) return false;
        Object result;
        try { result = Json.parse(message); }
        catch (IllegalArgumentException notJson) { return false; }
        return result instanceof Map<?, ?> fields && "stopped".equals(fields.get("status"))
            && fields.get("questions") instanceof List<?> questions && !questions.isEmpty();
    }

    // hook_action은 이 Hook이 세션에 한 일이다. none은 그대로 끝냄, fix_once는 한 번 이어서 고치게 함,
    // stopped는 세션을 멈춤, skipped는 검사하지 않음. 실행 관리가 이 값을 읽어 시도의 기록에 남긴다.
    private static void record(Path recordFile, Instant recordedAt, String turnId, boolean active,
                               String status, String action, String summary, String resultFile) throws IOException {
        String record = "{\"recorded_at\":\"" + recordedAt + "\","
            + "\"hook_event_name\":\"Stop\","
            + "\"turn_id\":\"" + jsonEscape(turnId) + "\","
            + "\"stop_hook_active\":" + active + ","
            + "\"check_status\":\"" + status + "\","
            + "\"hook_action\":\"" + action + "\","
            + "\"summary\":\"" + jsonEscape(summary) + "\","
            + "\"result_file\":\"" + jsonEscape(resultFile) + "\"}\n";
        Path target = recordFile.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, record, StandardCharsets.UTF_8);
    }

    private static Verdict judge(RunResult run, Path buildDir, Path root, Path logFile) {
        String log = root.relativize(logFile).toString();
        if (run.timedOut()) return new Verdict(Status.UNAVAILABLE, "문의 앱 검사가 " + TEST_TIMEOUT.toSeconds() + "초 안에 끝나지 않았습니다", log);
        if (run.error() != null) return new Verdict(Status.UNAVAILABLE, run.error(), log);

        if (run.exitCode() == 0) return new Verdict(Status.PASS, "Gradle 종료 코드가 0입니다", log);

        Path reportDir = buildDir.resolve("test-results/test");
        try {
            if (Files.isDirectory(reportDir)) {
                try (Stream<Path> files = Files.list(reportDir)) {
                    if (files.anyMatch(path -> path.getFileName().toString().matches("TEST-.*\\.xml"))) {
                        return new Verdict(Status.FAILURE, "Gradle 종료 코드가 " + run.exitCode()
                            + "입니다. 로그: " + log, root.relativize(reportDir).toString());
                    }
                }
            }
            return new Verdict(Status.UNAVAILABLE, "새 문의 앱 검사 결과 파일이 없습니다", log);
        } catch (IOException e) {
            return new Verdict(Status.UNAVAILABLE, "문의 앱 검사 결과를 읽을 수 없습니다: " + e.getMessage(), log);
        }
    }

    private static String actionFor(Verdict verdict, boolean alreadyContinued) {
        if (verdict.status() == Status.PASS) return "none";
        return verdict.status() == Status.FAILURE && !alreadyContinued ? "fix_once" : "stopped";
    }

    /**
     * 통과하면 그대로 끝낸다. 검사가 실패했고 이번 턴이 종료 Hook의 요청으로 이어진 것이 아니면(stop_hook_active가 false)
     * 한 번 이어서 고치게 한다. 이어진 턴에서도 실패하면 멈춘다. 검사의 결과를 얻지 못한 경우(실행 불가)에는
     * 원인이 환경인지 코드인지 이 Hook이 가리지 못하므로 고치게 하지 않고 멈추며, 이유와 로그 위치를 알린다.
     */
    private static String responseFor(Verdict verdict, boolean alreadyContinued) {
        if (verdict.status() == Status.PASS) return "{}";
        String kind = verdict.status() == Status.FAILURE ? "문의 앱 검사 실패" : "문의 앱 검사 실행 불가";
        String message = kind + ": " + verdict.summary() + ". 결과: " + verdict.resultFile();
        if (actionFor(verdict, alreadyContinued).equals("fix_once")) {
            return "{\"decision\":\"block\",\"reason\":\"" + jsonEscape(message
                + ". 실패한 입력·기대값·실제값을 대조해 코드를 고친 뒤 검사를 다시 실행하세요."
                + " 계획에서 온 검사의 기대값을 바꾸거나 그 검사를 지우거나 건너뛰지 마세요."
                + " 기대 결과를 바꿔야만 통과한다면 고치지 말고 멈춰 질문을 돌려주세요.") + "\"}";
        }
        return "{\"continue\":false,\"stopReason\":\"" + jsonEscape(kind)
            + "\",\"systemMessage\":\"" + jsonEscape(message) + "\"}";
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
                ProcessBuilder command = new ProcessBuilder(wrapper.toString(), "test", "--rerun-tasks", "--no-daemon",
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
                    // 인터럽트 표시를 먼저 되돌리면 아래의 기다림이 곧바로 끝난다. 정리한 뒤에 되돌린다.
                    stopProcess(process);
                    Thread.currentThread().interrupt();
                    return new RunResult(-1, false, "검사 대기가 중단됐습니다");
                }
            } catch (IOException e) {
                return new RunResult(-1, false, "Gradle을 시작할 수 없습니다: " + e.getMessage());
            }
        }

        // 종료를 요청하고 사라질 때까지 잠깐 기다린다. 로그 파일과 빌드 폴더를 쥔 프로세스를 남긴 채 돌아가지 않기 위해서다.
        private static void stopProcess(Process process) {
            List<ProcessHandle> all = new ArrayList<>(process.descendants().toList());
            all.add(process.toHandle());
            all.forEach(ProcessHandle::destroyForcibly);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (all.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
                try { Thread.sleep(100); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    /** 사건 JSON을 읽는 최소한의 해석기. Hook은 라이브러리 없이 소스 파일 하나로 실행되므로 직접 둔다. */
    static final class Json {
        private static final java.util.regex.Pattern NUMBER =
            java.util.regex.Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
        private final String text;
        private int at;

        private Json(String text) { this.text = text; }

        /** 객체는 Map, 배열은 List, 문자열은 String, 참·거짓은 Boolean, 수는 Double, null은 null로 돌려준다. */
        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.space();
            if (json.at != text.length()) throw json.broken();
            return value;
        }

        private Object value() {
            space();
            if (peek('{')) return object();
            if (peek('[')) return array();
            if (peek('"')) return string();
            if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
            if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
            if (text.startsWith("null", at)) { at += 4; return null; }
            int start = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String number = text.substring(start, at);
            if (!NUMBER.matcher(number).matches()) throw broken();
            return Double.valueOf(number);
        }

        private Map<String, Object> object() {
            Map<String, Object> members = new LinkedHashMap<>();
            at++;
            space();
            if (peek('}')) { at++; return members; }
            while (true) {
                space();
                if (!peek('"')) throw broken();
                String name = string();
                space();
                expect(':');
                members.put(name, value());
                space();
                if (peek(',')) { at++; continue; }
                expect('}');
                return members;
            }
        }

        private List<Object> array() {
            List<Object> items = new ArrayList<>();
            at++;
            space();
            if (peek(']')) { at++; return items; }
            while (true) {
                items.add(value());
                space();
                if (peek(',')) { at++; continue; }
                expect(']');
                return items;
            }
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            at++;
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw broken();
                if (c != '\\') { out.append(c); continue; }
                if (at >= text.length()) throw broken();
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw broken();
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            char hex = text.charAt(at + i);
                            int digit = hex < 0x80 ? Character.digit(hex, 16) : -1;
                            if (digit < 0) throw broken();
                            code = code * 16 + digit;
                        }
                        out.append((char) code);
                        at += 4;
                    }
                    case '"', '\\', '/' -> out.append(escaped);
                    default -> throw broken();
                }
            }
            throw broken();
        }

        private void space() { while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++; }
        private boolean peek(char c) { return at < text.length() && text.charAt(at) == c; }
        private void expect(char c) { if (!peek(c)) throw broken(); at++; }
        private IllegalArgumentException broken() { return new IllegalArgumentException("Invalid JSON at " + at); }
    }
}
