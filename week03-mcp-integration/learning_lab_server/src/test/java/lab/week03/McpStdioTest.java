package lab.week03;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static lab.week03.Json.*;
import static lab.week03.DraftStore.*;

class McpStdioTest {
    @TempDir Path temporary;
    McpSyncClient connect(boolean inspector) {
        var args=new ArrayList<>(List.of("-cp",System.getProperty("server.classpath"),inspector?"lab.week03.InspectorServer":"lab.week03.CatalogServer"));
        if(inspector) args.add(temporary.resolve("drafts").toString());
        var parameters=ServerParameters.builder(DraftChangesTest.javaCommand()).args(args).build();
        var client=McpClient.sync(new StdioClientTransport(parameters,Json.MAPPER)).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();return client;
    }
    static CallToolResult call(McpSyncClient client,String name,Map<String,Object> input) { return client.callTool(new CallToolRequest(name,input)); }
    static Map<String,Object> value(CallToolResult result) { assertFalse(result.isError(),result.toString());return map(result.structuredContent()); }
    static Map<String,Object> purchase(long quantity) {
        return object("items",List.of(object("product_id","NOTE-01","quantity",quantity),object("product_id","PEN-02","quantity",1)),"budget_krw",8000);
    }
    @Test void defaultServerExposesOnlyCatalogAndResourcePrompt() {
        try(var client=connect(false)) {
            assertEquals(Set.of("get_product","find_products","review_purchase"),new HashSet<>(client.listTools().tools().stream().map(Tool::name).toList()));
            assertEquals(3500,value(call(client,"get_product",Map.of("product_id","NOTE-01"))).get("price_krw"));
            assertEquals(false,value(call(client,"get_product",Map.of("product_id","PEN-02"))).get("in_stock"));
            assertEquals(8500,value(call(client,"review_purchase",purchase(2))).get("total_krw"));
            assertTrue(call(client,"get_product",Map.of("product_id","UNKNOWN")).isError());
            assertTrue(((TextResourceContents)client.readResource(new ReadResourceRequest("catalog://help")).contents().get(0)).text().contains("주문은 지원하지 않습니다"));
            assertTrue(((TextContent)client.getPrompt(new GetPromptRequest("explain_product",Map.of("product_id","NOTE-01"))).messages().get(0).content()).text().contains("get_product로 NOTE-01"));
            try { assertTrue(call(client,"save_purchase_draft",Map.of("preview_id","invented")).isError()); }
            catch(io.modelcontextprotocol.spec.McpError expected) { assertNotNull(expected.getJsonRpcError()); }
        }
        assertFalse(Files.exists(temporary.resolve("drafts")));
    }
    @Test void externalSchemasAndRuntimeRejectionsMatch() {
        try(var client=connect(false)) {
            var tools=client.listTools().tools();
            var find=tools.stream().filter(t->t.name().equals("find_products")).findFirst().orElseThrow();
            assertEquals(List.of("max_price_krw"),find.inputSchema().required());
            assertTrue(find.annotations().readOnlyHint());
            for(Object amount:List.of(-1,"8000",8000.1,true)) {
                var input=purchase(2);input.put("budget_krw",amount);
                var result=call(client,"review_purchase",input);assertTrue(result.isError());assertNull(result.structuredContent());
            }
            assertEquals(List.of(),value(call(client,"find_products",Map.of("max_price_krw",1499,"in_stock_only",false))).get("result"));
            assertTrue(call(client,"find_products",Map.of("max_price_krw",8000,"in_stock_only","true")).isError());
        }
    }
    @Test void inspectorSaveRetryConflictAndPreviewBoundary() throws Exception {
        try(var client=connect(true)) {
            var tools=client.listTools().tools();assertEquals(11,tools.size());
            var save=tools.stream().filter(t->t.name().equals("save_purchase_draft")).findFirst().orElseThrow();
            assertFalse(save.annotations().readOnlyHint());assertTrue(save.annotations().idempotentHint());
            var input=purchase(2);input.put("request_id","stdio-001");
            var preview=value(call(client,"preview_purchase_draft",input));
            assertFalse(Files.exists(temporary.resolve("drafts")));
            var token=object("preview_id",preview.get("preview_id"));
            assertEquals("saved",value(call(client,"save_purchase_draft",token)).get("status"));
            assertEquals("already_saved",value(call(client,"save_purchase_draft",token)).get("status"));
            byte[] original=Files.readAllBytes(temporary.resolve("drafts/stdio-001.json"));
            input.put("budget_krw",9000);
            var different=value(call(client,"preview_purchase_draft",input));
            assertTrue(call(client,"save_purchase_draft",object("preview_id",different.get("preview_id"))).isError());
            assertArrayEquals(original,Files.readAllBytes(temporary.resolve("drafts/stdio-001.json")));
        }
    }
    @Test void editRestartStatusRetryAndUndoOverRealStdio() throws Exception {
        Path root=temporary.resolve("drafts"),path=root.resolve("draft-001.json");
        DraftStore store=new DraftStore(root);store.save(DraftStoreTest.token(store.preview("draft-001",PurchaseContractTest.quote(1))));
        byte[] original=Files.readAllBytes(path);Map<String,Object> token;
        try(var client=connect(true)) {
            var input=purchase(2);input.put("draft_id","draft-001");input.put("operation_id","stdio-edit");
            var preview=value(call(client,"preview_draft_edit",input));token=object("preview_id",preview.get("preview_id"));
            assertEquals("applied",value(call(client,"apply_draft_change",token)).get("status"));
        }
        byte[] edited=Files.readAllBytes(path);var time=Files.getLastModifiedTime(path);
        try(var client=connect(true)) {
            assertEquals("applied",value(call(client,"get_draft_operation",Map.of("operation_id","stdio-edit"))).get("status"));
            assertTrue(call(client,"apply_draft_change",token).isError());
            assertEquals("already_applied",value(call(client,"resume_draft_operation",Map.of("operation_id","stdio-edit"))).get("status"));
            assertArrayEquals(edited,Files.readAllBytes(path));assertEquals(time,Files.getLastModifiedTime(path));
            var undo=value(call(client,"preview_draft_undo",Map.of("target_operation_id","stdio-edit","operation_id","stdio-undo")));
            value(call(client,"apply_draft_change",object("preview_id",undo.get("preview_id"))));
            assertArrayEquals(original,Files.readAllBytes(path));
            assertEquals(2,((List<?>)value(call(client,"get_draft_history",Map.of("draft_id","draft-001"))).get("result")).size());
        }
    }
}
