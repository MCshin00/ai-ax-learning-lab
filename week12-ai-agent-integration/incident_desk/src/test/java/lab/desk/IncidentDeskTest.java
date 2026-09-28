package lab.desk;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import static org.junit.jupiter.api.Assertions.*;
import static lab.desk.Models.*;

class IncidentDeskTest {
    @TempDir Path temp;
    Path data,requests;OperationsStore store;List<Source> sources;
    @BeforeEach void prepare()throws Exception {
        data=Files.createDirectories(temp.resolve("data"));requests=temp.resolve("requests");
        for(String name:List.of("services.json","runbooks.json"))Files.copy(Path.of("data",name),data.resolve(name));
        store=new OperationsStore(data,requests);sources=EvidenceSearch.load(data.resolve("runbooks.json"));
    }
    ReviewDesk desk(boolean agent){return new ReviewDesk(new IncidentFlow(store,ModelSetup.scripted(sources),agent),store);}
    @Test void bothFlowsGroundTheSameRequestAndAgentActuallyCallsTool() {
        var fixed=desk(false).analyze("a","VPN이 자꾸 끊겨요").analysis();
        var agent=desk(true).analyze("a","VPN이 자꾸 끊겨요").analysis();
        assertEquals("ready",fixed.status());assertEquals("ready",agent.status());
        assertEquals(fixed.items(),agent.items());assertEquals(2,fixed.modelCalls());assertEquals(3,agent.modelCalls());
        assertEquals("RB-VPN",agent.items().get(0).sources().get(0).id());
        assertTrue(agent.trace().stream().anyMatch(t->Json.write(t).contains("find_runbook")));
        assertFalse(Files.exists(requests));
    }
    @Test void asksResumesAndSeparatesConversations() {
        var desk=desk(true);
        assertEquals("needs_input",desk.analyze("a","접속이 안 돼요").analysis().status());
        var completed=desk.analyze("a","VPN이고 인증서 만료 메시지가 나와요").analysis();
        assertEquals("ready",completed.status());assertEquals("RB-VPN-CERT",completed.items().get(0).sources().get(0).id());
        assertEquals("ready",desk.analyze("a","인증서 만료 메시지가 나와요").analysis().status());
        assertEquals("needs_input",desk.analyze("b","인증서 만료 메시지가 나와요").analysis().status());
    }
    @Test void partialResultCanSaveOnlyConfirmedService() {
        var desk=desk(true);var p=desk.analyze("a","VPN과 급여 시스템이 안 돼요");
        assertEquals("partial",p.analysis().status());assertTrue(p.canSave());
        var saved=desk.save("a",p.id(),"req1","VPN 오류 확인 요청");
        var facts=store.read(saved.path("id").asText()).path("payload").path("facts");
        assertEquals(1,facts.size());assertEquals("VPN",facts.get(0).path("id").asText());
    }
    @Test void editAndSaveAreDurableAndIdempotent() {
        var desk=desk(true);var p=desk.analyze("a","VPN이 자꾸 끊겨요");
        var first=desk.save("a",p.id(),"req1","오전 9시 인증서 만료. 갱신 안내 요청.");
        assertEquals("saved",first.path("status").asText());
        assertEquals("already_saved",desk.save("a",p.id(),"req1","오전 9시 인증서 만료. 갱신 안내 요청.").path("status").asText());
        assertEquals("conflict",desk.save("a",p.id(),"req1","다른 내용").path("status").asText());
        assertEquals("오전 9시 인증서 만료. 갱신 안내 요청.",new OperationsStore(data,requests).read(first.path("id").asText()).path("payload").path("text").asText());
    }
    @Test void consoleRejectsAnOldSaveCommandAfterCorrection() {
        var desk=desk(true);var old=DeskApp.handle(desk,"a|VPN이 자꾸 끊겨요");
        String oldSave="/save|a|"+old.path("id").asText()+"|req1|VPN 문의";
        var fresh=DeskApp.handle(desk,"a|VPN이 아니라 통합 로그인 문제예요");
        assertEquals("stale_preview",DeskApp.handle(desk,oldSave).path("status").asText());
        assertFalse(Files.exists(requests));
        String save="/save|a|"+fresh.path("id").asText()+"|req2|SSO 문의";
        assertEquals("saved",DeskApp.handle(desk,save).path("status").asText());
        assertEquals("already_saved",DeskApp.handle(desk,save).path("status").asText());
    }
    @Test void correctionInvalidatesOldPreviewAndStatusChangeRejectsSave()throws Exception {
        var desk=desk(true);var old=desk.analyze("a","VPN이 자꾸 끊겨요");
        var next=desk.analyze("a","VPN이 아니라 통합 로그인 문제예요");
        assertEquals("SSO",next.analysis().items().get(0).serviceId());
        assertEquals("stale_preview",desk.save("a",old.id(),"req1","VPN 요청").path("status").asText());
        String content=Files.readString(data.resolve("services.json"));
        Files.writeString(data.resolve("services.json"),content.replace("\"revision\":1","\"revision\":2"));
        assertEquals("stale_facts",desk.save("a",next.id(),"req2","SSO 요청").path("status").asText());
        var fresh=desk.analyze("a","VPN이 아니라 통합 로그인 문제예요");
        assertEquals("saved",desk.save("a",fresh.id(),"req3","상태 재확인 후 SSO 요청").path("status").asText());
    }
    @Test void absentOrWrongServiceEvidenceCannotProduceReady() {
        for(var available:List.of(List.<Source>of(),List.of(sources.get(1)))) {
            var ai=ModelSetup.scripted(available);
            var result=new IncidentFlow(store,ai,true).analyze("a","VPN이 자꾸 끊겨요");
            assertEquals("review",result.status());assertTrue(result.items().get(0).sources().isEmpty());
        }
    }
    @Test void lookupFailureRetainsOtherService() {
        Operations failing=new Operations(){public Lookup lookup(String id){if(id.equals("SSO"))throw new IllegalStateException();return store.lookup(id);}
            public com.fasterxml.jackson.databind.JsonNode save(com.fasterxml.jackson.databind.JsonNode input){return store.save(input);}};
        var result=new IncidentFlow(failing,ModelSetup.scripted(sources),true).analyze("a","VPN이 끊기고 통합 로그인도 느려요");
        assertEquals("partial",result.status());assertEquals("unavailable",result.items().get(1).status());
    }
    @Test void repeatedModelToolRequestsStopAtSixCalls() {
        var ai=new ModelWork((stage,input)->stage.equals("intake")?ScriptedModels.model(stage,input):
            ScriptedModels.sequence(java.util.stream.IntStream.range(0,8).mapToObj(i->AiMessage.from(ToolExecutionRequest.builder()
                .id("repeat-"+i).name("find_runbook").arguments("{\"serviceId\":\"VPN\",\"query\":\"VPN\"}").build())).toList()),
            new EvidenceSearch(ScriptedModels.embeddings(),sources),"TEST");
        var result=new IncidentFlow(store,ai,true).analyze("a","VPN이 자꾸 끊겨요");
        assertEquals("limit_reached",result.status());assertEquals(6,result.modelCalls());assertNotNull(result.items().get(0).facts());
    }
    @Test void invalidModelAndMissingSettingsAreNotSuccess() {
        var ai=new ModelWork((stage,input)->ScriptedModels.sequence(List.of(AiMessage.from("invalid"))),
            new EvidenceSearch(ScriptedModels.embeddings(),sources),"TEST");
        assertEquals("processing_failed",new IncidentFlow(store,ai,true).analyze("a","anything").status());
        assertThrows(IllegalArgumentException.class,()->ModelSetup.live(name->null,sources));
    }
    @Test void realMcpDiscoveryLookupWriteAndIndependentRead() {
        try(var client=new OperationsClient(data,requests)) {
            assertEquals(Set.of("get_service_status","save_work_request","read_work_request"),
                new HashSet<>(client.tools().stream().map(t->t.name()).toList()));
            assertEquals("found",client.lookup("VPN").status());assertEquals("not_found",client.lookup("unknown").status());
            var desk=new ReviewDesk(new IncidentFlow(client,ModelSetup.scripted(sources),true),client);
            var p=desk.analyze("a","VPN이 자꾸 끊겨요");var result=desk.save("a",p.id(),"mcp-1","검토한 문의");
            assertEquals("saved",result.path("status").asText());
            try(var second=new OperationsClient(data,requests)) {
                var read=second.call("read_work_request",Map.of("id",result.path("id").asText()));
                assertEquals("검토한 문의",read.path("payload").path("text").asText());
            }
        }
    }
}
