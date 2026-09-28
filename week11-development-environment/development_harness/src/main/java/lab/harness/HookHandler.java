package lab.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;

public final class HookHandler {
    public record Result(String status,String detail) {}
    @FunctionalInterface public interface Check { Result run() throws Exception; }
    private final Path project;
    private final Check check;
    public HookHandler(Path project,Check check) { this.project=project; this.check=check; }
    public Map<String,Object> handle(JsonNode event) throws Exception {
        String name=event.path("hook_event_name").asText();
        Path local=project.resolve(".local/harness"); Files.createDirectories(local);
        if(name.equals("SessionStart")) {
            String context=Files.readString(project.resolve("docs/project.md"));
            Path checkpoint=local.resolve("checkpoint.md");
            if(Files.isRegularFile(checkpoint)) context+="\n최근 인계 내용(현재 파일·검사와 대조):\n"+Files.readString(checkpoint);
            return Map.of("hookSpecificOutput",Map.of("hookEventName",name,"additionalContext",context));
        }
        if(name.equals("PostToolUse")) {
            // Tool arguments, outputs and conversation text are deliberately not collected.
            append(local.resolve("events.jsonl"),Map.of("event",name,"tool",event.path("tool_name").asText(),
                "session",event.path("session_id").asText(),"turn",event.path("turn_id").asText()));
            return Map.of();
        }
        if(!name.equals("Stop")) return Map.of();
        Result result;
        try { result=check.run(); }
        catch(Exception e) { result=new Result("UNAVAILABLE","검사 설정 또는 프로세스를 확인하세요: "+e.getClass().getSimpleName()); }
        Files.writeString(local.resolve("last-check.json"),HarnessMain.JSON.writeValueAsString(result)+"\n");
        append(local.resolve("events.jsonl"),Map.of("event",name,"status",result.status(),
            "session",event.path("session_id").asText(),"turn",event.path("turn_id").asText()));
        if(result.status().equals("PASSED")) return Map.of();
        if(result.status().equals("FAILED") && !event.path("stop_hook_active").asBoolean(false))
            return Map.of("decision","block","reason",result.detail()+" 요구와 실패를 대조해 수정하고 다시 확인하세요. 자동 수정 요청은 이번 한 번입니다.");
        return Map.of("continue",false,"systemMessage",result.detail()+" 자동 수정을 종료했습니다. 다음 조치를 검토하세요.");
    }
    private static void append(Path path,Object row) throws Exception {
        byte[] line=(HarnessMain.JSON.writeValueAsString(row)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND);
            var lock=channel.lock()) { channel.write(java.nio.ByteBuffer.wrap(line)); }
    }
}
