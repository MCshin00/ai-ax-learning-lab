package lab.week11;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsultationTest {
    static final class Script implements AiPort {
        final Queue<Reply> replies = new ArrayDeque<>();
        final List<com.fasterxml.jackson.databind.JsonNode> inputs = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        Script(Reply... values) { replies.addAll(List.of(values)); }
        public Reply invoke(String operation, Object payload, int remaining) {
            inputs.add(Json.tree(payload)); limits.add(remaining); return replies.remove();
        }
    }
    static AiPort.Reply response(Object value, int calls, List<AiPort.Source> sources) {
        return new AiPort.Reply("ok", Json.tree(value), calls, sources, List.of(), "SCRIPTED_TEST");
    }
    static AiPort.Reply intake(String kind, String... ids) {
        return response(new Consultation.Intake(kind,List.of(ids),Map.of(),Map.of()),1,List.of());
    }
    static AiPort.Reply draft(List<String> ids, List<String> sources, List<AiPort.Source> evidence, int calls) {
        return response(new Consultation.Draft(ids.stream().map(id -> new Consultation.DraftItem(id,"확인한 안내",sources)).toList()),calls,evidence);
    }
    static AiPort.Source source(String scope) {
        return new AiPort.Source("POL-CANCEL","취소","정책 본문",1.0,scope);
    }
    static Consultation app(AiPort ai) throws Exception { return Application.create(ai); }

    @Test void orderFactsReachDraftAndResultWithoutPolicySearch() throws Exception {
        var script = new Script(intake("status","A-102"),draft(List.of("A-102"),List.of(),List.of(),1));
        var result = app(script).reply("one","A-102 상태가 궁금해요.");
        assertEquals("ready",result.status);
        assertEquals("배송 준비",result.items.get(0).facts.path("status").asText());
        assertEquals("배송 준비",script.inputs.get(1).path("orders").get(0).path("facts").path("status").asText());
        assertEquals(List.of(6,5),script.limits);
        assertEquals(2,result.modelCalls);
        assertEquals("display",result.actions.get(0).action());
    }
    @Test void javaExpandsEvidenceAndSendsFeedbackWithRemainingBudget() throws Exception {
        var script = new Script(intake("cancel","A-102"),
            draft(List.of("A-102"),List.of("POL-CANCEL"),List.of(source("excerpt")),2),
            draft(List.of("A-102"),List.of("POL-CANCEL"),List.of(source("full")),1));
        var result = app(script).reply("one","A-102 취소");
        assertEquals("ready",result.status);
        assertEquals(1,result.revisions); assertEquals(4,result.modelCalls);
        assertEquals(List.of(6,5,3),script.limits);
        assertFalse(script.inputs.get(2).path("feedback").isEmpty());
        assertTrue(script.inputs.get(2).path("evidence").get(0).path("text").asText().contains("신청 경로"));
    }
    @Test void missingNumberResumesAndCorrectionsClearOldUserFacts() throws Exception {
        var extra = response(new Consultation.Intake("return",List.of("A-104"),Map.of("A-104",2),Map.of("A-104",false)),1,List.of());
        var script = new Script(intake("return"),extra,
            draft(List.of("A-104"),List.of(),List.of(),1), intake("status","A-102"),
            draft(List.of("A-102"),List.of(),List.of(),1),intake("return"));
        var app = app(script);
        assertEquals("needs_input",app.reply("one","반품하고 싶어요").status);
        app.reply("one","A-104 수령 2일 미사용");
        assertEquals("return",script.inputs.get(1).path("previous").path("intent").asText());
        assertEquals("ready",app.reply("one","A-102 상태").status);
        assertTrue(script.inputs.get(3).path("previous").path("received_days").isEmpty());
        app.reply("two","반품 문의");
        assertTrue(script.inputs.get(5).path("previous").isNull());
    }
    @Test void partialLookupFailureKeepsSuccessfulOrder() throws Exception {
        var ids = List.of("A-999","A-102","A-103");
        var script = new Script(intake("status",ids.toArray(String[]::new)),draft(ids,List.of(),List.of(),1));
        var lookup = new OrderLookup(Path.of("data/orders.json"));
        var app = new Consultation(script,id -> {
            if (id.equals("A-103")) throw new IllegalStateException("가짜 조회 장애");
            return lookup.apply(id);
        },Application.policies());
        var result = app.reply("one","A-999 A-102 A-103 상태");
        assertEquals("partial",result.status);
        assertEquals(List.of("not_found","ready","unavailable"),result.items.stream().map(i -> i.status).toList());
    }
    @Test void callLimitStopsRevisionButKeepsFacts() throws Exception {
        var script = new Script(intake("cancel","A-102"),
            draft(List.of("A-102"),List.of("POL-CANCEL"),List.of(source("excerpt")),2));
        var app = new Consultation(script,new OrderLookup(Path.of("data/orders.json")),Application.policies(),3,true,"excerpt");
        var result = app.reply("one","A-102 취소");
        assertEquals("limit_reached",result.status); assertEquals(3,result.modelCalls);
        assertEquals("배송 준비",result.items.get(0).facts.path("status").asText());
        assertEquals(2,script.inputs.size());
    }
    @Test void unsolvedOrEmptyEvidenceDoesNotBecomeAnAnswer() throws Exception {
        var script = new Script(intake("cancel","A-102"),draft(List.of("A-102"),List.of("MADE-UP"),List.of(),2));
        var result = app(script).reply("one","A-102 취소");
        assertEquals("needs_review",result.status); assertEquals(0,result.revisions);
        assertEquals("",result.items.get(0).answer);
    }
    @Test void transportFailurePreservesFactsAndMarksUnknownCallCount() throws Exception {
        var script = new Script(intake("status","A-102"));
        AiPort port = (operation,payload,remaining) -> {
            if (operation.equals("draft")) throw new AiPort.Unavailable();
            return script.invoke(operation,payload,remaining);
        };
        var result = app(port).reply("one","A-102 상태");
        assertEquals("unavailable",result.status); assertFalse(result.modelCallsKnown);
        assertEquals("배송 준비",result.items.get(0).facts.path("status").asText());
    }
    @Test void invalidNumberAndDuplicateResultAreNotPublished() throws Exception {
        var invalid = app(new Script(intake("status","A-1024"))).reply("one","A-1024 상태");
        assertEquals("invalid_output",invalid.status);
        var script = new Script(intake("status","A-102"),draft(List.of("A-102","A-102"),List.of(),List.of(),1));
        var result = app(script).reply("one","A-102 상태");
        assertEquals("needs_review",result.status); assertEquals("",result.items.get(0).answer);
    }
    @Test void apiChecksInputTypesAndPreservesBusinessStatus() throws Exception {
        var app = app(new Script(intake("status","A-102"),draft(List.of("A-102"),List.of(),List.of(),1)));
        assertEquals(400,Application.dispatch(app,"{\"conversation_id\":1,\"request\":\"x\"}").code());
        var response = Application.dispatch(app,"{\"conversation_id\":\"one\",\"request\":\"A-102 상태\"}");
        assertEquals(200,response.code()); assertEquals("ready",Json.tree(response.body()).path("status").asText());
    }
    @Test void emptyAnswerAndUnrequestedOrderCannotBecomeReady() throws Exception {
        var empty = response(new Consultation.Draft(List.of(new Consultation.DraftItem("A-102","  ",List.of()))),1,List.of());
        var first = app(new Script(intake("status","A-102"),empty)).reply("one","A-102 상태");
        assertEquals("needs_review",first.status);
        assertEquals("review",first.items.get(0).nextAction);
        var extra = draft(List.of("A-102","A-103"),List.of(),List.of(),1);
        var second = app(new Script(intake("status","A-102"),extra)).reply("two","A-102 상태");
        assertEquals("invalid_output",second.status);
        assertEquals("",second.items.get(0).answer);
        assertEquals("배송 준비",second.items.get(0).facts.path("status").asText());
    }
}
