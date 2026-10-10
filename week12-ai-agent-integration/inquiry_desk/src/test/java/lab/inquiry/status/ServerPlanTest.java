package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static lab.inquiry.status.PlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerPlanTest {
    @TempDir static Path directory;
    private McpSyncClient client;

    @BeforeAll void start() { client = connect(server(directory)); }
    @AfterAll void stop() { if (client != null) client.closeGracefully(); }
    @BeforeEach void reset() throws Exception { write(directory, data()); }

    private CallToolResult call(Map<String, Object> input) {
        return client.callTool(new CallToolRequest("get_service_status", input));
    }

    private CallToolResult get(String id) { return call(Map.of("serviceId", id)); }

    @Test void S2_absent() { assertWire(get("PAYROLL"), ABSENT, false); }
    @Test void S3_caseSensitive() { assertWire(get("vpn"), "{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"vpn\"}", false); }
    @Test void S5_emptyArgument() { assertWire(get(""), INVALID, true); }
    @Test void S8_extraArgument() { assertWire(call(Map.of("serviceId", "VPN", "extra", true)), INVALID, true); }

    @Test void S9_missingFile() throws Exception {
        Files.delete(directory.resolve("services.json"));
        assertWire(get("VPN"), UNREADABLE, true);
    }
    @Test void S10_notJson() throws Exception {
        Files.writeString(directory.resolve("services.json"), "not json");
        assertWire(get("VPN"), INVALID_SSO.replace("SSO", "VPN"), true);
    }
    @Test void S11_notArray() throws Exception {
        write(directory, Map.of());
        assertWire(get("VPN"), INVALID_SSO.replace("SSO", "VPN"), true);
    }

    @Test void S12_otherBadRowDoesNotHideVpn() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(1)).remove("detail"); write(directory, rows);
        assertWire(get("VPN"), VPN, false);
    }
    @Test void S13_requestedBadRow() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(1)).remove("detail"); write(directory, rows);
        assertWire(get("SSO"), INVALID_SSO, true);
    }
    @Test void unknownStateIsPreserved() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(1)).put("state", "maintenance"); write(directory, rows);
        assertWire(get("SSO"), "{\"outcome\":\"FOUND\",\"serviceId\":\"SSO\",\"state\":\"maintenance\",\"detail\":\"일부 계정에서 로그인 지연이 발생해 운영팀이 확인 중이다. 복구 시각은 미정이다.\",\"revision\":1}", false);
    }
    @Test void S19_unknownRowField() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(0)).put("future", "value"); write(directory, rows);
        assertWire(get("VPN"), VPN, false);
    }
    @Test void S21_rereadsStateAndRevision() throws Exception {
        assertWire(get("VPN"), VPN, false);
        var rows = data(); ((ObjectNode) rows.get(0)).put("state", "incident").put("revision", 2); write(directory, rows);
        assertWire(get("VPN"), VPN.replace("normal", "incident").replace("\"revision\":1", "\"revision\":2"), false);
    }
    @Test void declarationDescribesEveryOutcomeAndRejectsBrokenErrors() throws Exception {
        var tools = client.listTools().tools();
        Tool tool = tools.stream().filter(item -> item.name().equals("get_service_status")).findFirst().orElseThrow();
        assertEquals("get_service_status", tool.name());
        assertEquals("서비스 ID로 현재 서비스 상태를 조회한다. 자료에 없는 서비스는 NOT_FOUND로 돌려준다.", tool.description());
        assertEquals(List.of("serviceId"), tool.inputSchema().required());
        assertEquals(false, tool.inputSchema().additionalProperties());
        assertEquals(Map.of("type", "string"), tool.inputSchema().properties().get("serviceId"));
        assertEquals(StatusWire.outputSchema(), tool.outputSchema());
        var validator = McpJsonDefaults.getSchemaValidator();
        for (String example : List.of(VPN, ABSENT, UNREADABLE, INVALID_SSO, INVALID)) {
            ObjectNode value = (ObjectNode) StatusWire.JSON.readTree(example);
            assertTrue(validator.validate(tool.outputSchema(), StatusWire.JSON.convertValue(value, Map.class)).valid());
            for (var fields = value.fieldNames(); fields.hasNext();) {
                String required = fields.next();
                ObjectNode missing = value.deepCopy(); missing.remove(required);
                assertFalse(validator.validate(tool.outputSchema(), StatusWire.JSON.convertValue(missing, Map.class)).valid(), required);
            }
        }
        for (String example : List.of(UNREADABLE, INVALID)) {
            ObjectNode value = (ObjectNode) StatusWire.JSON.readTree(example);
            value.put("code", "RATE_LIMITED");
            assertFalse(validator.validate(tool.outputSchema(), StatusWire.JSON.convertValue(value, Map.class)).valid());
        }
    }
}
