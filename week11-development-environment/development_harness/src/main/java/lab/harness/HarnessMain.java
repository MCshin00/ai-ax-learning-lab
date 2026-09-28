package lab.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 코딩 도구에 검사와 자료를 연결합니다. 모델 호출은 코딩 도구가 담당합니다. */
public final class HarnessMain {
    static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("setup|hook|mcp <프로젝트 폴더>");
        Path project = Path.of(args[1]).toAbsolutePath().normalize();
        switch (args[0]) {
            case "setup" -> setup(project);
            case "hook" -> {
                var event = JSON.readTree(System.in);
                byte[] result=JSON.writeValueAsBytes(new HookHandler(project, new GradleCheck(project)).handle(event));
                System.out.write(result); System.out.write('\n'); System.out.flush();
            }
            case "mcp" -> AcceptanceServer.start(project);
            default -> throw new IllegalArgumentException("setup|hook|mcp 중 하나를 사용하세요.");
        }
    }
    static void setup(Path project) throws Exception {
        if (!Files.isRegularFile(project.resolve("harness.json")))
            throw new IllegalArgumentException("대상 프로젝트에 harness.json이 필요합니다.");
        Path jar=Path.of(HarnessMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!jar.toString().endsWith(".jar")) throw new IllegalStateException("harnessJar로 만든 JAR를 실행하세요.");
        Path local=project.resolve(".local/harness"), hooks=project.resolve(".codex/hooks.json");
        Files.createDirectories(local);
        if (Files.exists(hooks)) throw new IllegalStateException("기존 hooks.json을 유지합니다. 기존 연결에 필요한 항목을 IDE에서 병합하세요.");
        String windows="java -jar "+quoteWindows(jar.toString())+" hook "+quoteWindows(project.toString());
        String posix="java -jar "+quotePosix(jar.toString())+" hook "+quotePosix(project.toString());
        var handler=Map.of("type","command","command",posix,"commandWindows",windows,"timeout",150);
        var events=new LinkedHashMap<String,Object>();
        events.put("SessionStart",List.of(Map.of("matcher","startup|resume","hooks",List.of(handler))));
        events.put("PostToolUse",List.of(Map.of("hooks",List.of(handler))));
        events.put("Stop",List.of(Map.of("hooks",List.of(handler))));
        Files.createDirectories(hooks.getParent());
        Files.writeString(hooks,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("hooks",events))+"\n",StandardOpenOption.CREATE_NEW);
        // This snippet is local: its process arguments contain this machine's paths.
        String snippet="[mcp_servers.acceptance_cases]\ncommand = \"java\"\nargs = "+JSON.writeValueAsString(List.of("-jar",jar.toString(),"mcp",project.toString()))+"\n";
        Files.writeString(local.resolve("mcp-snippet.toml"),snippet,StandardCharsets.UTF_8);
        System.out.println(".codex/hooks.json 과 .local/harness/mcp-snippet.toml을 만들었습니다. 프로젝트에서 /hooks로 검토하고 MCP 연결을 설정하세요.");
    }
    static String quoteWindows(String value) {
        if (value.contains("\"") || value.contains("%") || value.contains("!"))
            throw new IllegalArgumentException("따옴표·%·! 없는 실습 경로를 사용하세요.");
        return "\""+value+"\"";
    }
    static String quotePosix(String value) { return "'"+value.replace("'","'\"'\"'")+"'"; }
}
