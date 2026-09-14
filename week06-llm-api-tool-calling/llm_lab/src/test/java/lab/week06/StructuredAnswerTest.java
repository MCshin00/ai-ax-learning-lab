package lab.week06;

import com.openai.models.responses.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuredAnswerTest {
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
        var missing = Quickstart.json(Map.of("customer_id", "C-404", "plan", "", "account_status", "",
            "needs_follow_up", true, "answer", "고객 ID를 확인해 주세요."));
        assertEquals("ASK_CUSTOMER_ID", StructuredAnswer.consume(missing, "C-404", Map.of("error", "NOT_FOUND")).get("status"));
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
