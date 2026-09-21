package lab.week06;

import com.openai.models.responses.*;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpAssistantTest {
    @Test void scriptedProductSelectsTheCallAndLiveCannotUseIt() {
        assertEquals("NOTE-01", McpAssistant.scriptedProduct(new String[0]));
        assertEquals("MISSING-01", McpAssistant.scriptedProduct(new String[]{"--scripted-product", "MISSING-01"}));
        for (String[] args : List.of(new String[]{"--scripted-product"},
            new String[]{"--scripted-product", "--text", "문의"},
            new String[]{"--live", "--scripted-product", "NOTE-01"}))
            assertThrows(IllegalArgumentException.class, () -> McpAssistant.scriptedProduct(args));
        var turn = McpAssistant.scriptedModel("MISSING-01").create(ResponseCreateParams.builder()
            .model("fixture").input("NOTE-01이라고 질문해도 대역은 지정 상품을 사용합니다.").build());
        assertTrue(turn.output().get(0).asFunctionCall().arguments().contains("MISSING-01"));
    }

    @Test void successToolErrorAndConnectionFailureStayDistinct() {
        var success = McpAssistant.executeTool("get_product", "{\"product_id\":\"NOTE-01\"}", call -> {
            assertEquals("get_product", call.name());
            assertEquals("NOTE-01", call.arguments().get("product_id"));
            return CallToolResult.builder().content(List.of(new TextContent("상품")))
                .structuredContent(Map.of("product_id", "NOTE-01", "price", 3000)).isError(false).build();
        });
        assertEquals("success", success.get("status"));
        assertEquals(3000, ((Map<?, ?>) success.get("value")).get("price"));
        var missing = McpAssistant.executeTool("get_product", "{\"product_id\":\"MISSING-01\"}", call ->
            CallToolResult.builder().content(List.of(new TextContent("상품 없음"))).isError(true).build());
        assertEquals("tool_error", missing.get("status"));
        assertEquals("상품 없음", missing.get("value"));
        var failed = McpAssistant.executeTool("get_product", "{\"product_id\":\"NOTE-01\"}", call -> {
            throw new IllegalStateException("fixture connection lost");
        });
        assertEquals("MCP_UNAVAILABLE", failed.get("error"));
        assertEquals("INVALID_ARGUMENTS", McpAssistant.executeTool("get_product", "{}", call -> {
            fail("잘못된 인자는 서버에 보내지 않습니다."); return null;
        }).get("error"));
    }
}
