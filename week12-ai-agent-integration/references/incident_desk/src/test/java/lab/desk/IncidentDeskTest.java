package lab.desk;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.BiFunction;
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
            // 교정 검색은 한 번만 시도하고, 근거가 없으면 초안을 다시 쓰지 않습니다.
            assertEquals(1,result.trace().stream().filter(t->Json.write(t).contains("rewrite_query")).count());
            assertEquals(4,result.modelCalls());
        }
    }
    @Test void correctiveSearchRecoversVocabularyMismatch() {
        for(boolean agent:List.of(false,true)) {
            var result=desk(agent).analyze("a","사내망 연결이 자꾸 끊겨요").analysis();
            assertEquals("ready",result.status());assertEquals("RB-VPN",result.items().get(0).sources().get(0).id());
            var steps=result.trace().stream().map(Json::write).toList();
            int rewrite=steps.indexOf(steps.stream().filter(s->s.contains("rewrite_query")).findFirst().orElseThrow());
            assertTrue(steps.get(rewrite-1).contains("no_evidence"));assertTrue(steps.get(rewrite+1).contains("RB-VPN"));
            assertEquals(agent?5:4,result.modelCalls());
        }
    }
    /** 지정한 검색어를 임베딩할 때만 장애를 냅니다. 색인 준비는 정상입니다. */
    static EmbeddingModel failingOn(Set<String> queries) {
        var base=ScriptedModels.embeddings();
        return new EmbeddingModel(){@Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            if(segments.size()==1&&queries.contains(segments.get(0).text()))throw new IllegalStateException("검색 장애");
            return base.embedAll(segments);
        }};
    }
    IncidentFlow flow(BiFunction<String,JsonNode,ChatModel> models,EmbeddingModel embeddings,boolean agent) {
        return new IncidentFlow(store,new ModelWork(models,new EvidenceSearch(embeddings,sources),"TEST"),agent);
    }
    static AiMessage findRunbook(String id,String serviceId,String query) {
        return AiMessage.from(ToolExecutionRequest.builder().id(id).name("find_runbook")
            .arguments(Json.write(Map.of("serviceId",serviceId,"query",query))).build());
    }
    @Test void searchFailureIsReportedWithoutRewriting() {
        for(boolean agent:List.of(false,true)) {
            var result=flow(ScriptedModels::model,failingOn(Set.of("VPN 연결 끊김")),agent).analyze("a","VPN이 자꾸 끊겨요");
            assertEquals("review",result.status());assertTrue(result.items().get(0).answer().contains("검색에 실패"));
            assertTrue(result.trace().stream().noneMatch(t->Json.write(t).contains("rewrite_query")));
            assertEquals(agent?3:2,result.modelCalls());
        }
    }
    @Test void failureDuringCorrectionIsReportedAsSearchFailure() {
        for(boolean agent:List.of(false,true)) {
            var result=flow(ScriptedModels::model,failingOn(Set.of("VPN 연결 오류")),agent).analyze("a","사내망 연결이 자꾸 끊겨요");
            assertEquals("review",result.status());assertTrue(result.items().get(0).answer().contains("검색에 실패"));
            assertEquals(1,result.trace().stream().filter(t->Json.write(t).contains("rewrite_query")).count());
            assertEquals(agent?4:3,result.modelCalls());
        }
    }
    @Test void appSearchesWhenAgentSkipsTheTool() {
        // 에이전트 경로이지만 모델이 검색 도구를 부르지 않고 초안만 반환합니다.
        BiFunction<String,JsonNode,ChatModel> skipping=(stage,input)->ScriptedModels.model(stage,
            stage.equals("plan")?((ObjectNode)input.deepCopy()).put("agent",false):input);
        for(var c:List.of(Map.entry("VPN이 자꾸 끊겨요",3),Map.entry("사내망 연결이 자꾸 끊겨요",4))) {
            var events=new ArrayList<String>();
            var result=flow(skipping,ScriptedModels.embeddings(),true).analyze("a",c.getKey(),e->events.add(Json.write(e)));
            // 처리 단계도 앱 검색 → 검색 결과 → 다시 작성 순서로 전달됩니다.
            int app=indexOf(events,"app_search");
            assertTrue(app>=0&&events.get(app+1).contains("find_runbook"));
            assertTrue(events.subList(app,events.size()).stream().anyMatch(e->e.contains("\"draft\"")));
            assertEquals("ready",result.status());assertEquals("RB-VPN",result.items().get(0).sources().get(0).id());
            assertTrue(result.trace().stream().anyMatch(t->Json.write(t).contains("app_search")));
            assertEquals(c.getValue()==4,result.trace().stream().anyMatch(t->Json.write(t).contains("rewrite_query")));
            assertEquals(c.getValue(),result.modelCalls());
        }
    }
    static int indexOf(List<String> events,String text) {
        for(int i=0;i<events.size();i++)if(events.get(i).contains(text))return i;return -1;
    }
    @Test void searchFailureIsReportedWhenCorrectionHitsCallLimit() {
        // 첫 초안까지 5회를 쓰고 검색어 재작성이 6번째 호출입니다. SSO는 새 근거를 찾아 다시 작성하려다 한도에 걸립니다.
        BiFunction<String,JsonNode,ChatModel> busy=(stage,input)->!stage.equals("plan")?ScriptedModels.model(stage,input):
            ScriptedModels.sequence(List.of(findRunbook("s1","VPN","접속 끊김"),findRunbook("s2","SSO","접속 끊김"),
                findRunbook("s3","VPN","접속 끊김"),AiMessage.from(Json.write(new Plan(List.of())))));
        var result=flow(busy,failingOn(Set.of("VPN 연결 오류")),true).analyze("a","VPN이 끊기고 통합 로그인도 느려요");
        assertEquals("limit_reached",result.status());assertEquals(6,result.modelCalls());
        assertTrue(result.items().get(0).answer().contains("검색에 실패"));
        assertFalse(result.items().get(1).answer().contains("검색에 실패"));
    }
    @Test void searchFailureIsReportedWhenFirstDraftHitsCallLimit() {
        // 첫 검색이 장애로 끝난 뒤 모델이 같은 검색만 반복해 첫 초안을 완성하기 전에 한도에 걸립니다.
        BiFunction<String,JsonNode,ChatModel> looping=(stage,input)->stage.equals("intake")?ScriptedModels.model(stage,input):
            ScriptedModels.sequence(java.util.stream.IntStream.range(0,8).mapToObj(i->findRunbook("s"+i,"VPN","장애 검색어")).toList());
        var result=flow(looping,failingOn(Set.of("장애 검색어")),true).analyze("a","VPN이 자꾸 끊겨요");
        assertEquals("limit_reached",result.status());assertEquals(6,result.modelCalls());
        assertTrue(result.items().get(0).answer().contains("검색에 실패"));
    }
    @Test void evidenceFoundAfterSearchFailureStaysReady() {
        var plan=new Plan(List.of(new Proposal("VPN","VPN 연결 오류 안내",List.of("RB-VPN"))));
        BiFunction<String,JsonNode,ChatModel> retrying=(stage,input)->stage.equals("intake")?ScriptedModels.model(stage,input):
            ScriptedModels.sequence(List.of(findRunbook("s1","VPN","장애 검색어"),findRunbook("s2","VPN","VPN 연결 끊김"),AiMessage.from(Json.write(plan))));
        var result=flow(retrying,failingOn(Set.of("장애 검색어")),true).analyze("a","VPN이 자꾸 끊겨요");
        assertEquals("ready",result.status());assertEquals("VPN 연결 오류 안내",result.items().get(0).answer());
        assertTrue(result.trace().stream().map(Json::write).anyMatch(t->t.contains("unavailable")));
        assertTrue(result.trace().stream().map(Json::write).noneMatch(t->t.contains("rewrite_query")||t.contains("app_search")));
    }
    @Test void httpStreamsStagesBeforeResultAndSavesSeparately()throws Exception {
        try(var server=new DeskServer(desk(true),0)) {
            var http=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.port();
            java.util.function.BiFunction<String,String,java.net.http.HttpResponse<String>> post=(path,body)->{
                try{return http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());}
                catch(Exception e){throw new IllegalStateException(e);}
            };
            var stream=post.apply("/inquiries/stream",Json.write(Map.of("conversationId","a","text","사내망 연결이 자꾸 끊겨요")));
            assertTrue(stream.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
            var events=Arrays.stream(stream.body().split("\n\n")).filter(e->!e.isBlank()).toList();
            assertTrue(events.get(0).startsWith("event: stage")&&events.get(0).contains("\"intake\""));
            assertTrue(events.stream().anyMatch(e->e.contains("rewrite_query")));
            var last=events.get(events.size()-1);assertTrue(last.startsWith("event: result"));
            var preview=Json.read(last.substring(last.indexOf("data: ")+6));
            assertEquals("ready",preview.path("analysis").path("status").asText());assertFalse(Files.exists(requests));
            var saved=post.apply("/requests",Json.write(Map.of("conversationId","a","previewId",preview.path("id").asText(),
                "requestId","http-1","text","사내망(VPN) 연결 끊김. 오류 메시지 확인 요청.")));
            assertEquals("saved",Json.read(saved.body()).path("status").asText());
            var needsInput=post.apply("/inquiries",Json.write(Map.of("conversationId","b","text","접속이 안 돼요")));
            assertEquals(200,needsInput.statusCode());assertEquals("needs_input",Json.read(needsInput.body()).path("analysis").path("status").asText());
            assertEquals(400,post.apply("/inquiries","not json").statusCode());
            assertEquals(400,post.apply("/inquiries/stream",Json.write(Map.of("conversationId","a"))).statusCode());
            var get=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/inquiries")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(405,get.statusCode());
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
