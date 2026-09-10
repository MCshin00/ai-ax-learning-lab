package lab.week06;

import com.openai.models.responses.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuickstartTest {
    @Test void plainRequestContainsNoCustomerToolOrInstructions() {
        var client = new Quickstart.OfflineClient("normal");
        Quickstart.run("요약", client, "offline", true);
        assertTrue(client.requests.get(0).tools().isEmpty());
        assertFalse(client.requests.get(0).instructions().orElseThrow().contains("고객"));
    }
    @Test void actualLookupResultReturnsWithMatchingCallId() {
        var client = new Quickstart.OfflineClient("normal");
        var old = Quickstart.CUSTOMERS.put("C-100", Map.of("plan", "changed-plan", "status", "active"));
        try {
            var result = Quickstart.run("C-100", client, "offline", false);
            assertTrue(result.get("answer").toString().contains("changed-plan"));
            assertEquals(2, client.requests.size());
            var input = client.requests.get(1).input().orElseThrow().asResponse();
            assertEquals("offline-1", input.get(input.size() - 1).asFunctionCallOutput().callId().orElseThrow());
            assertEquals("offline-1", input.get(1).asFunctionCall().callId());
        } finally { Quickstart.CUSTOMERS.put("C-100", old); }
    }
    @Test void missingIdDoesNotExecuteTool() {
        var client = new Quickstart.OfflineClient("missing");
        assertEquals(List.of(), Quickstart.run("조회", client, "offline", false).get("tool_results"));
        assertEquals(1, client.requests.size());
    }
    @Test void unknownCustomerReturnsAnErrorToTheModel() {
        var client = new Quickstart.OfflineClient("unknown");
        var result = Quickstart.run("C-404", client, "offline", false);
        assertEquals("MODEL_RESPONSE", result.get("status"));
        assertTrue(result.get("answer").toString().contains("CUSTOMER_NOT_FOUND"));
        assertEquals(2, client.requests.size());
    }
    @Test void malformedAndUnregisteredCallsCannotBecomeSuccess() {
        assertEquals("UNKNOWN_TOOL", Quickstart.executeCall("delete_customer", "{}").get("error"));
        for (String value : List.of("oops", "[]", "null", "{}", "{\"customer_id\":\"C-100\"} {}", "{\"customer_id\":\"C-100\",\"admin\":true}"))
            assertEquals("INVALID_ARGUMENTS", Quickstart.executeCall("get_customer_context", value).get("error"));
        for (String value : List.of("100", "true", "null", "\" \""))
            assertEquals("INVALID_CUSTOMER_ID", Quickstart.executeCall("get_customer_context", "{\"customer_id\":" + value + "}").get("error"));
    }
    @Test void incompleteAndRefusedResponsesAreNotSuccess() {
        assertEquals("INCOMPLETE", Quickstart.run("요청", p -> new Quickstart.Turn("incomplete", "partial", List.of(), false), "offline", false).get("status"));
        assertEquals("REFUSED", Quickstart.run("요청", p -> new Quickstart.Turn("completed", "declined", List.of(), true), "offline", false).get("status"));
    }
    @Test void reasoningItemsArePreservedBeforeToolResult() {
        var fixture = new Quickstart.OfflineClient("normal");
        Quickstart.Gateway gateway = p -> {
            var turn = fixture.create(p);
            if (fixture.requests.size() != 1) return turn;
            var reasoning = ResponseReasoningItem.builder().id("reasoning-1").summary(List.of()).encryptedContent("fixture-only").build();
            return new Quickstart.Turn(turn.status(), turn.text(), List.of(ResponseOutputItem.ofReasoning(reasoning), turn.output().get(0)), false);
        };
        Quickstart.run("C-100", gateway, "offline", false);
        var input = fixture.requests.get(1).input().orElseThrow().asResponse();
        assertEquals("reasoning-1", input.get(1).asReasoning().id());
        assertEquals("fixture-only", input.get(1).asReasoning().encryptedContent().orElseThrow());
        assertEquals("offline-1", input.get(3).asFunctionCallOutput().callId().orElseThrow());
    }
    @Test void repeatedCallsStopAtTheRequestBudgetBeforeAnotherToolExecution() {
        var client = new Quickstart.OfflineClient("repeat");
        var result = Quickstart.run("C-100", client, "offline", false);
        assertEquals("STOPPED", result.get("status"));
        assertEquals(3, result.get("model_requests"));
        assertEquals(2, ((List<?>) result.get("tool_results")).size());
        assertTrue(client.requests.stream().allMatch(p -> !p.tools().isEmpty()));
    }
    @Test void reportedUsageIsKeptAndMissingUsageIsNotInvented() {
        var usage = new Quickstart.Usage(100, 20, 30, 10);
        var result = Quickstart.run("요약", p -> new Quickstart.Turn("completed", "결과", List.of(), false, usage), "offline", true);
        var turn = (Map<?, ?>) ((List<?>) result.get("turns")).get(0);
        assertEquals(usage, turn.get("usage"));
        var offline = Quickstart.run("C-100", new Quickstart.OfflineClient("normal"), "offline", false);
        var missing = (Map<?, ?>) ((List<?>) offline.get("turns")).get(0);
        assertEquals("UNAVAILABLE", missing.get("usage_status"));
        assertNull(missing.get("usage"));
    }
}
