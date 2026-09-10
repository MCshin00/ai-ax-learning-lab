package lab.week05.example;

import com.google.gson.*;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lab.week05.example.Execution.*;

/** 비교 예제 자체의 진입점. 다른 엔진을 이 클래스에 등록할 필요가 없다. */
public final class EngineCli {
    private static List<String> strings(JsonElement values) {
        if (values == null || !values.isJsonArray()) return List.of();
        return values.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
    }
    public static Job load(Path file) throws Exception {
        JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        String platform = System.getProperty("os.name").startsWith("Windows") ? "windows" : "posix";
        JsonObject commands = data.has("commands") ? data.getAsJsonObject("commands").getAsJsonObject(platform) : null;
        return new Job(data.has("goal") ? data.get("goal").getAsString() : "",
            Path.of(data.has("workspace") ? data.get("workspace").getAsString() : ".").toAbsolutePath().normalize(),
            strings(data.get("context_files")), commands == null ? List.of() : strings(commands.get("execute")),
            commands == null ? List.of() : strings(commands.get("verify")),
            data.has("repair_limit") ? data.get("repair_limit").getAsInt() : 0,
            data.has("timeout_seconds") ? data.get("timeout_seconds").getAsInt() : 600);
    }
    private static Executor simulatedRepair() {
        Queue<Result> values = new ArrayDeque<>(List.of(new Result(Kind.OK, 0, "완료했습니다"),
            new Result(Kind.FAILED, 1, "누락 id: invalid-waiver"), new Result(Kind.OK, 0, "수정했습니다"),
            new Result(Kind.OK, 0, "8개 입력과 결과가 일치합니다")));
        return (command, workspace, input, seconds) -> values.remove();
    }
    private static void save(Path file, JsonObject output) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(output), StandardCharsets.UTF_8);
    }
    static int run(Path file, String architecture, boolean simulated, Path report) {
        JsonObject output = new JsonObject();
        output.addProperty("run_id", UUID.randomUUID().toString());
        output.addProperty("architecture", architecture);
        output.addProperty("executor", simulated ? "SIMULATED_RESPONSE" : "COMMAND_PROCESS");
        output.addProperty("state", "PREPARING");
        try {
            save(report, output);
            Files.deleteIfExists(report.getParent().resolve("agent-message.txt"));
            Job job = load(file);
            if (Files.isDirectory(job.workspace())) {
                Files.createDirectories(job.workspace().resolve(".local/reference-engine"));
                Files.deleteIfExists(job.workspace().resolve(".local/reference-engine/agent-message.txt"));
            }
            Executor executor = simulated ? simulatedRepair() : Execution.process();
            JsonObject result;
            if (architecture.equals("state")) result = new Gson().toJsonTree(new StateEngine().run(job, executor)).getAsJsonObject();
            else if (architecture.equals("linear")) {
                result = new Gson().toJsonTree(new LinearEngine().run(job, executor)).getAsJsonObject();
                result.add("state", result.remove("status"));
            } else throw new IllegalArgumentException("비교 구조는 state 또는 linear입니다.");
            result.entrySet().forEach(entry -> output.add(entry.getKey(), entry.getValue()));
        } catch (Exception error) {
            output.addProperty("state", "STOPPED"); output.addProperty("reason", error.getMessage());
        }
        try { save(report, output); }
        catch (Exception error) { System.err.println("실행 상태 기록 실패: " + error.getMessage()); return 1; }
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(output));
        return output.get("state").getAsString().equals("SUCCEEDED") ? 0 : 1;
    }
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length < 1 || args.length > 3) {
            System.err.println("사용법: reference-engine.jar <작업.json> [state|linear] [demo-repair]"); System.exit(1);
        }
        if (args.length == 3 && !args[2].equals("demo-repair")) {
            System.err.println("가상 응답 선택은 demo-repair입니다."); System.exit(1);
        }
        System.exit(run(Path.of(args[0]), args.length > 1 ? args[1] : "state", args.length == 3,
            Path.of(".local/reference-engine/current.json")));
    }
}
