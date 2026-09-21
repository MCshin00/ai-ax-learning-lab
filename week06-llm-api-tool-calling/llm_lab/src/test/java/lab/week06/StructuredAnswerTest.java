package lab.week06;

import com.openai.models.responses.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuredAnswerTest {
    @Test void offlineEntryPointPreservesNullFieldsAndChoosesFollowUp() throws Exception {
        for (String id : List.of("C-404", "", "C-100")) {
            var bytes = new java.io.ByteArrayOutputStream();
            var previous = System.out;
            try (var output = new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
                System.setOut(output);
                StructuredAnswer.main(new String[]{"--customer", id});
            } finally { System.setOut(previous); }
            var result = Quickstart.ARGUMENTS.readTree(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("SCRIPTED_OFFLINE", result.path("mode").asText());
            boolean missing = !id.equals("C-100");
            assertEquals(missing ? "ASK_CUSTOMER_ID" : "SHOW_ACCOUNT",
                result.path("result").path("next_action").asText(), id);
            var data = result.path("result").path("data");
            assertEquals(id, data.path("customer_id").asText());
            assertEquals(missing, data.path("needs_follow_up").asBoolean());
            for (String field : List.of("plan", "account_status")) {
                assertTrue(data.has(field), field);
                assertEquals(missing, data.get(field).isNull(), field);
            }
        }
    }

    @Test void mcpClasspathResolvesWildcardBeforeStartingChild(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        java.nio.file.Files.createFile(temp.resolve("server.jar"));
        java.nio.file.Files.createFile(temp.resolve("sdk.jar"));
        java.nio.file.Files.createFile(temp.resolve("notes.md"));
        String resolved = McpAssistant.resolvedClasspath(temp + "/*");
        assertFalse(resolved.contains("*"));
        assertEquals(Set.of(temp.resolve("server.jar").toString(), temp.resolve("sdk.jar").toString()),
            Set.of(resolved.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))));
        assertThrows(IllegalArgumentException.class, () -> McpAssistant.resolvedClasspath(temp.resolve("absent") + "/*"));
    }

    String answer(String plan, boolean missing) {
        return Quickstart.json(Map.of("customer_id", "C-100", "plan", plan, "account_status", "active",
            "needs_follow_up", missing, "answer", "계정 조회 안내"));
    }
    @Test void finalResponseSchemaAndValidatedDataDriveNextStep() {
        var result = StructuredAnswer.run("C-100", request -> {
            assertTrue(request.text().orElseThrow().format().orElseThrow().isJsonSchema());
            return new Quickstart.Turn("completed", answer("basic", false), List.of(), false);
        }, "fixture");
        assertEquals("SHOW_ACCOUNT", result.get("status"));
        assertEquals("FACT_MISMATCH", StructuredAnswer.consume(answer("premium", false), "C-100",
            CustomerDirectory.lookup("C-100")).get("status"));
        assertEquals("INVALID_OUTPUT", StructuredAnswer.consume("{} {}", "C-100", Map.of()).get("status"));
    }
    @Test void refusalIncompleteAndMissingCustomerAreNotSuccessfulAccounts() {
        assertEquals("REFUSED", StructuredAnswer.run("C-100",
            req -> new Quickstart.Turn("completed", "", List.of(), true), "fixture").get("status"));
        assertEquals("INCOMPLETE", StructuredAnswer.run("C-100",
            req -> new Quickstart.Turn("incomplete", "{", List.of(), false), "fixture").get("status"));
        var missing = "{\"customer_id\":\"C-404\",\"plan\":null,\"account_status\":null,\"needs_follow_up\":true,\"answer\":\"고객 ID를 확인해 주세요.\"}";
        assertEquals("ASK_CUSTOMER_ID", StructuredAnswer.consume(missing, "C-404",
            Map.of("error", "CUSTOMER_NOT_FOUND")).get("next_action"));
    }

    @Test void suppliedPartialFactsAreUsedWithoutLookingUpOtherFields() {
        var facts = Map.<String, Object>of("customer_id", "C-100", "plan", "trial");
        var raw = "{\"customer_id\":\"C-100\",\"plan\":\"trial\",\"account_status\":null,\"needs_follow_up\":false,\"answer\":\"trial 요금제입니다.\"}";
        var result = StructuredAnswer.run("C-100", facts, request -> {
            assertTrue(request.input().orElseThrow().asText().contains("trial"));
            assertFalse(request.input().orElseThrow().asText().contains("active"));
            return new Quickstart.Turn("completed", raw, List.of(), false);
        }, "fixture");
        assertEquals("SHOW_ACCOUNT", result.get("next_action"));
        var data = (Map<?, ?>) result.get("data");
        assertTrue(data.containsKey("account_status"));
        assertNull(data.get("account_status"));
        var output = assertDoesNotThrow(() -> Quickstart.ARGUMENTS.readTree(StructuredAnswer.json(result)));
        assertTrue(output.path("data").has("account_status"));
        assertTrue(output.path("data").get("account_status").isNull());
        assertEquals("HOLD", StructuredAnswer.consume(raw.replace("null", "\"active\""), "C-100", facts).get("next_action"));
        assertEquals("FACT_MISMATCH", StructuredAnswer.consume(raw.replace("trial", "basic"), "C-100", facts).get("status"));
    }

    @Test void followUpIsNotHoldAndUnexpectedToolErrorsDoNotAskForAnId() {
        var missing = "{\"customer_id\":\"\",\"plan\":null,\"account_status\":null,\"needs_follow_up\":true,\"answer\":\"고객 번호를 알려 주세요.\"}";
        assertEquals("ASK_CUSTOMER_ID", StructuredAnswer.run("", request ->
            new Quickstart.Turn("completed", missing, List.of(), false), "fixture").get("next_action"));
        var failure = StructuredAnswer.run("C-100", Map.of("error", "INVALID_ARGUMENTS"), request -> {
            fail("도구 계약 오류를 고객 부재처럼 모델에 전달하면 안 됩니다."); return null;
        }, "fixture");
        assertEquals("TOOL_ERROR", failure.get("status"));
        assertEquals("HOLD", failure.get("next_action"));
        assertEquals("PROVIDER_ERROR", StructuredAnswer.run("C-100", request -> {
            throw new IllegalStateException("fixture");
        }, "fixture").get("status"));
    }

    @Test void factsForAnotherCustomerAreRejectedBeforeGeneration() {
        var facts = Map.<String, Object>of("customer_id", "C-200", "plan", "trial");
        var result = StructuredAnswer.run("C-100", facts, request -> {
            fail("다른 고객의 조회값을 생성 근거로 사용하면 안 됩니다."); return null;
        }, "fixture");
        assertEquals("FACT_MISMATCH", result.get("status"));
        assertEquals("HOLD", result.get("next_action"));
    }
    @Test void externalToolContractUsesSameBoundedLoopAndMatchingResult() {
        var requests = new ArrayList<ResponseCreateParams>();
        var tool = FunctionTool.builder().name("get_product").description("상품 조회").strict(false)
            .parameters(FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", com.openai.core.JsonValue.from("object"))
                .putAdditionalProperty("properties", com.openai.core.JsonValue.from(Map.of("product_id", Map.of("type", "string"))))
                .putAdditionalProperty("required", com.openai.core.JsonValue.from(List.of("product_id"))).build()).build();
        Quickstart.Gateway model = req -> {
            requests.add(req);
            if (requests.size() == 1) return new Quickstart.Turn("completed", "", List.of(ResponseOutputItem.ofFunctionCall(
                ResponseFunctionToolCall.builder().name("get_product").arguments("{\"product_id\":\"NOTE-01\"}")
                    .callId("mcp-result").build())), false);
            return new Quickstart.Turn("completed", "상품 안내", List.of(), false);
        };
        var result = Quickstart.runWithTools("상품 문의", model, "fixture", List.of(tool), "상품 도구 사용",
            (name, args) -> Map.of("status", "success", "product_id", "NOTE-01"), 3);
        assertEquals("MODEL_RESPONSE", result.get("status"));
        var history = requests.get(1).input().orElseThrow().asResponse();
        var output = history.get(history.size()-1).asFunctionCallOutput();
        assertEquals("mcp-result", output.callId().orElseThrow());
        assertTrue(output.output().asString().contains("NOTE-01"));
    }
}
