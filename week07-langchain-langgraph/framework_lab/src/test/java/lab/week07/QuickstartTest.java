package lab.week07;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuickstartTest {
    @Test void actualFrameworkReturnsCurrentToolData() {
        var old = Quickstart.CUSTOMERS.put("C-100", Map.of("plan", "changed-plan", "status", "active"));
        try {
            var model = new Quickstart.OfflineModel("normal");
            var result = Quickstart.run("C-100", model);
            assertEquals("MODEL_RESPONSE", result.get("status"));
            assertTrue(result.get("answer").toString().contains("changed-plan"));
            assertEquals(2, model.requests.size());
            assertEquals(1, ((List<?>) result.get("tool_results")).size());
            assertEquals("get_customer_context", model.requests.get(0).toolSpecifications().get(0).name());
        } finally { Quickstart.CUSTOMERS.put("C-100", old); }
    }
    @Test void missingIdDoesNotRunTool() {
        var model = new Quickstart.OfflineModel("missing");
        assertEquals(List.of(), Quickstart.run("조회", model).get("tool_results"));
        assertEquals(1, model.requests.size());
    }
    @Test void unknownCustomerCannotBePublishedAsSuccessAfterModelReadsError() {
        var model = new Quickstart.OfflineModel("unknown");
        var result = Quickstart.run("C-404", model);
        assertEquals("TOOL_ERROR", result.get("status"));
        assertTrue(result.get("tool_results").toString().contains("CUSTOMER_NOT_FOUND"));
        assertEquals(2, model.requests.size());
    }
    @Test void unregisteredAndMalformedCallsAreErrors() {
        for (String name : List.of("delete_customer", "get_customer_context")) {
            var model = new Quickstart.OfflineModel("normal") {
                @Override public ChatResponse doChat(ChatRequest request) {
                    var response = super.doChat(request);
                    if (!response.aiMessage().hasToolExecutionRequests()) return response;
                    return ChatResponse.builder().finishReason(FinishReason.TOOL_EXECUTION).aiMessage(AiMessage.from(
                            ToolExecutionRequest.builder().id("invalid-1").name(name).arguments("not-json").build())).build();
                }
            };
            assertEquals("TOOL_ERROR", Quickstart.run("조회", model).get("status"));
        }
    }
    @Test void responseLimitsAndRefusalAreNotNormalAnswers() {
        for (FinishReason reason : List.of(FinishReason.LENGTH, FinishReason.CONTENT_FILTER, FinishReason.OTHER)) {
            var model = new Quickstart.OfflineModel("normal") {
                @Override public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(AiMessage.from("partial")).finishReason(reason).build();
                }
            };
            assertEquals(reason == FinishReason.CONTENT_FILTER ? "REFUSED" : "INCOMPLETE", Quickstart.run("요청", model).get("status"));
        }
    }
    @Test void frameworkEnforcesToolRoundTripLimit() {
        var model = new Quickstart.OfflineModel("normal") {
            @Override public ChatResponse doChat(ChatRequest request) {
                requests.add(request);
                return ChatResponse.builder().finishReason(FinishReason.TOOL_EXECUTION).aiMessage(AiMessage.from(
                        ToolExecutionRequest.builder().id("repeat-" + requests.size()).name("get_customer_context")
                                .arguments("{\"customer_id\":\"C-100\"}").build())).build();
            }
        };
        var result = Quickstart.run("조회", model);
        assertEquals("STOPPED", result.get("status"));
        assertTrue(model.requests.size() <= 3);
    }
    @Test void adapterRefusalExceptionIsReportedAsRefused() {
        var model = new Quickstart.OfflineModel("normal") {
            @Override public ChatResponse doChat(ChatRequest request) {
                throw new ContentFilteredException("fixture refusal");
            }
        };
        assertEquals("REFUSED", Quickstart.run("요청", model).get("status"));
    }
    @Test void malformedTrailingJsonCannotExecuteLookup() {
        for (String arguments : List.of("{\"customer_id\":\"C-100\"} {}", "{\"customer_id\":100}", "{\"customer_id\":true}")) {
        var model = new Quickstart.OfflineModel("normal") {
            @Override public ChatResponse doChat(ChatRequest request) {
                var response = super.doChat(request);
                if (!response.aiMessage().hasToolExecutionRequests()) return response;
                return ChatResponse.builder().finishReason(FinishReason.TOOL_EXECUTION).aiMessage(AiMessage.from(
                        ToolExecutionRequest.builder().id("invalid-trailing").name("get_customer_context")
                                .arguments(arguments).build())).build();
            }
        };
        assertEquals("TOOL_ERROR", Quickstart.run("조회", model).get("status"));
        }
    }
}
