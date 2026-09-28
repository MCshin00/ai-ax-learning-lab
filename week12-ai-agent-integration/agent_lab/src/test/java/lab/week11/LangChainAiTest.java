package lab.week11;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LangChainAiTest {
    static AiMessage searchCall() {
        return AiMessage.from(ToolExecutionRequest.builder().id("search-1").name("search_policy")
            .arguments("{\"query\":\"취소 출고\"}").build());
    }
    static Map<String,Object> payload() {
        return Map.of("request","문의","evidence",List.of(),"initial_context","excerpt");
    }
    static AiMessage draft() {
        return AiMessage.from(ScriptedModels.modelJson(new Consultation.Draft(List.of(
            new Consultation.DraftItem("A-102","확인한 안내",List.of("POL-CANCEL"))))));
    }
    @Test void toolResultReachesNextModelCallAndSourcesRemainOutsideModelText() throws Exception {
        var policies = Application.policies();
        var requests = new ArrayList<ChatRequest>();
        var scripted = ScriptedModels.sequence(List.of(searchCall(), draft()));
        ChatModel capture = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                requests.add(request); return scripted.chat(request);
            }
        };
        var search = new PolicySearch(ScriptedModels.embeddings(), policies);
        var ai = new LangChainAi((op,p) -> capture, search::search, policies,"TEST");
        var result = ai.invoke("draft",payload(),6);
        assertEquals("ok",result.status()); assertEquals(2,result.modelCalls());
        assertTrue(result.sources().stream().anyMatch(s -> s.sourceId().equals("POL-CANCEL")));
        assertEquals("excerpt",result.sources().get(0).scope());
        assertTrue(requests.get(1).messages().stream().anyMatch(m -> m instanceof ToolExecutionResultMessage t
            && t.id().equals("search-1") && t.text().contains("POL-CANCEL")));
        assertEquals("A-102",result.value().path("items").get(0).path("order_id").asText());
    }
    @Test void repeatedToolsCannotExceedRemainingModelCalls() throws Exception {
        var calls = new int[1];
        ChatModel repeating = new ChatModel() {
            @Override public ChatResponse doChat(ChatRequest request) {
                calls[0]++; return ChatResponse.builder().aiMessage(searchCall()).build();
            }
        };
        var ai = new LangChainAi((op,p) -> repeating,q -> new PolicySearch.Result("no_evidence",List.of()),List.of(),"TEST");
        var result = ai.invoke("draft",payload(),3);
        assertEquals("limit_reached",result.status()); assertEquals(3,result.modelCalls()); assertEquals(3,calls[0]);
        assertEquals(0,ai.invoke("intake",payload(),0).modelCalls());
    }
    @Test void noEvidenceAndSearchFailureAreDifferentToolResults() {
        for (String status : List.of("no_evidence","unavailable")) {
            var ai = new LangChainAi((op,p) -> ScriptedModels.sequence(List.of(searchCall(), draft())), q -> {
                if (status.equals("unavailable")) throw new IllegalStateException("가짜 검색 장애");
                return new PolicySearch.Result(status,List.of());
            },List.of(),"TEST");
            var result=ai.invoke("draft",payload(),6);
            assertEquals("ok",result.status()); assertTrue(result.sources().isEmpty());
            assertEquals(status,result.trace().get(0).path("result").path("status").asText());
        }
    }
    @Test void malformedResponseIsDifferentFromProviderFailureAndBothCountAttempts() {
        var broken = new LangChainAi((op,p) -> ScriptedModels.sequence(List.of(AiMessage.from("not json"))),
            q -> new PolicySearch.Result("no_evidence",List.of()),List.of(),"TEST");
        var invalid=broken.invoke("intake",payload(),6);
        assertEquals("invalid_output",invalid.status());assertEquals(1,invalid.modelCalls());
        var failed=new LangChainAi((op,p) -> ScriptedModels.sequence(List.of()),
            q -> new PolicySearch.Result("no_evidence",List.of()),List.of(),"TEST");
        var unavailable=failed.invoke("intake",payload(),6);
        assertEquals("unavailable",unavailable.status());assertEquals(1,unavailable.modelCalls());
    }
    @Test void emptyStoreAndThresholdProduceNoEvidence() throws Exception {
        var empty=new PolicySearch(ScriptedModels.embeddings(),List.of());
        assertEquals("no_evidence",empty.search("취소").status());
        assertEquals("invalid_input",empty.search(" ").status());
        var search=new PolicySearch(ScriptedModels.embeddings(),Application.policies());
        assertEquals("no_evidence",search.search("무관한 입력").status());
    }
    @Test void configurationUsesOnlyInjectedFakeSettings() {
        assertThrows(IllegalArgumentException.class,() -> ModelSetup.configuration(name -> null));
        var settings=Map.of("AI_AX_LIVE","1","OPENAI_API_KEY","fake-test-value",
            "OPENAI_MODEL","fake-chat","OPENAI_EMBEDDING_MODEL","fake-embedding");
        var config=ModelSetup.configuration(settings::get);
        assertEquals("fake-chat",config.chatModel());assertEquals("fake-embedding",config.embeddingModel());
        assertFalse(config.toString().contains("fake-test-value"));
    }
}
