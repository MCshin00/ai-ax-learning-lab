package lab.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {
    @TempDir Path project;
    private com.fasterxml.jackson.databind.JsonNode event(String name,boolean active) {
        return HarnessMain.JSON.valueToTree(Map.of("hook_event_name",name,"session_id","fixture-session", "turn_id","fixture-turn",
            "stop_hook_active",active,"tool_name","apply_patch","tool_input",Map.of("command","uncollected")));
    }
    @Test void failedCheckContinuesOnceThenChecksAgainAndStops() throws Exception {
        var count=new AtomicInteger();
        var handler=new HookHandler(project,()->{ count.incrementAndGet(); return new HookHandler.Result("FAILED","CSV 줄바꿈 실패"); });
        assertEquals("block",handler.handle(event("Stop",false)).get("decision"));
        assertEquals(false,handler.handle(event("Stop",true)).get("continue"));
        assertEquals(2,count.get());
        assertEquals("FAILED",HarnessMain.JSON.readTree(project.resolve(".local/harness/last-check.json").toFile()).path("status").asText());
    }
    @Test void correctionIsRecheckedAndMayFinish() throws Exception {
        var count=new AtomicInteger();
        var handler=new HookHandler(project,()->new HookHandler.Result(count.getAndIncrement()==0?"FAILED":"PASSED","fixture"));
        assertEquals("block",handler.handle(event("Stop",false)).get("decision"));
        assertTrue(handler.handle(event("Stop",true)).isEmpty());
    }
    @Test void environmentFailureDoesNotRequestCodeRepair() throws Exception {
        var handler=new HookHandler(project,()->{ throw new java.io.IOException("fixture"); });
        var result=handler.handle(event("Stop",false));
        assertEquals(false,result.get("continue")); assertFalse(result.containsKey("decision"));
    }
    @Test void startupAndResumeReadCurrentProjectAndCheckpoint() throws Exception {
        Files.createDirectories(project.resolve("docs")); Files.writeString(project.resolve("docs/project.md"),"현재 기능");
        var handler=new HookHandler(project,()->new HookHandler.Result("PASSED",""));
        String initial=HarnessMain.JSON.writeValueAsString(handler.handle(event("SessionStart",false)));
        assertTrue(initial.contains("현재 기능"));
        Files.writeString(project.resolve(".local/harness/checkpoint.md"),"남은 작업: 팀 필터");
        String resumed=HarnessMain.JSON.writeValueAsString(handler.handle(event("SessionStart",false)));
        assertTrue(resumed.contains("팀 필터"));
    }
    @Test void recordsContainMetadataAndSeparateWorkspaces() throws Exception {
        var one=new HookHandler(project,()->new HookHandler.Result("PASSED",""));
        one.handle(event("PostToolUse",false));
        String log=Files.readString(project.resolve(".local/harness/events.jsonl"));
        assertTrue(log.contains("apply_patch")); assertFalse(log.contains("uncollected"));
        Path other=project.resolve("other"); new HookHandler(other,()->new HookHandler.Result("PASSED","")).handle(event("Stop",false));
        assertFalse(Files.readString(other.resolve(".local/harness/events.jsonl")).contains("apply_patch"));
    }
    @Test void emptySkippedOrMissingReportsAreNotPassing() throws Exception {
        assertEquals("UNAVAILABLE",GradleCheck.classify(project,0,"log").status());
        Files.writeString(project.resolve("TEST.xml"),"<testsuite tests='1' failures='0' errors='0' skipped='1'/>");
        assertEquals("UNAVAILABLE",GradleCheck.classify(project,0,"log").status());
        Files.writeString(project.resolve("TEST.xml"),"<testsuite tests='1' failures='1' errors='0'><testcase classname='Export' name='newline'><failure/></testcase></testsuite>");
        assertEquals("FAILED",GradleCheck.classify(project,1,"log").status());
        assertTrue(GradleCheck.classify(project,1,"log").detail().contains("Export.newline"));
        Files.writeString(project.resolve("TEST.xml"),"<testsuite tests='1' failures='0' errors='0'/>");
        assertEquals("UNAVAILABLE",GradleCheck.classify(project,1,"log").status());
        assertEquals("PASSED",GradleCheck.classify(project,0,"log").status());
    }
    @Test void acceptanceLookupReturnsSourceExampleAndRejectsUnknownId() throws Exception {
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/acceptance-cases.json"),"[{\"id\":\"C1\",\"expected\":\"literal\"}]");
        assertTrue(HarnessMain.JSON.writeValueAsString(AcceptanceServer.lookup(project,"C1")).contains("literal"));
        assertThrows(IllegalArgumentException.class,()->AcceptanceServer.lookup(project,"missing"));
    }
    @Test void actualMcpDiscoveryAndKnownAndMissingCase() throws Exception {
        Files.createDirectories(project.resolve("data"));
        Files.copy(Path.of("data/acceptance-cases.json"),project.resolve("data/acceptance-cases.json"));
        String javaCommand=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var server=io.modelcontextprotocol.client.transport.ServerParameters.builder(javaCommand)
            .args("-cp",System.getProperty("server.classpath"),"lab.harness.HarnessMain","mcp",project.toString()).build();
        var transport=new io.modelcontextprotocol.client.transport.StdioClientTransport(server,io.modelcontextprotocol.json.McpJsonDefaults.getMapper());
        try(var client=io.modelcontextprotocol.client.McpClient.sync(transport).requestTimeout(java.time.Duration.ofSeconds(15)).build()) {
            client.initialize();
            assertEquals(List.of("get_acceptance_case"),client.listTools().tools().stream().map(t->t.name()).toList());
            var ok=client.callTool(new io.modelcontextprotocol.spec.McpSchema.CallToolRequest("get_acceptance_case",Map.of("id","CSV-QUOTE")));
            assertFalse(Boolean.TRUE.equals(ok.isError()));
            assertTrue(HarnessMain.JSON.writeValueAsString(ok.structuredContent()).contains("expectedCell"));
            var missing=client.callTool(new io.modelcontextprotocol.spec.McpSchema.CallToolRequest("get_acceptance_case",Map.of("id","unknown")));
            assertTrue(missing.isError());
        }
    }
}
