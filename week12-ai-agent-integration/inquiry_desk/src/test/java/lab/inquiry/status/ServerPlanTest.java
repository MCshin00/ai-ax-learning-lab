package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test void S1_providedVpn() { assertWire(get("VPN"), VPN, false); }
    @Test void S2_absent() { assertWire(get("PAYROLL"), ABSENT, false); }
    @Test void S3_caseSensitive() { assertWire(get("vpn"), "{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"vpn\"}", false); }
    @Test void S4_missingArgument() { assertWire(call(Map.of()), INVALID, true); }
    @Test void S5_emptyArgument() { assertWire(get(""), INVALID, true); }
    @Test void S6_blankArgument() { assertWire(get(" \t "), INVALID, true); }

    static Stream<Map<String, Object>> wrongArguments() {
        return Stream.of(Map.of("serviceId", 7), Collections.singletonMap("serviceId", null));
    }
    @ParameterizedTest @MethodSource("wrongArguments")
    void S7_wrongType(Map<String, Object> input) { assertWire(call(input), INVALID, true); }
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

    static Stream<String> badRows() {
        return Stream.of("state-unknown", "state-empty", "detail-missing", "detail-null", "detail-blank",
                "revision-missing", "revision-string", "revision-fraction", "revision-negative");
    }
    private void badSso(String variant) throws Exception {
        var rows = data();
        ObjectNode row = (ObjectNode) rows.get(1);
        switch (variant) {
            case "state-unknown" -> row.put("state", "maintenance");
            case "state-empty" -> row.put("state", "");
            case "detail-missing" -> row.remove("detail");
            case "detail-null" -> row.putNull("detail");
            case "detail-blank" -> row.put("detail", " \t");
            case "revision-missing" -> row.remove("revision");
            case "revision-string" -> row.put("revision", "1");
            case "revision-fraction" -> row.put("revision", 1.5);
            case "revision-negative" -> row.put("revision", -1);
            default -> throw new AssertionError(variant);
        }
        write(directory, rows);
    }

    @ParameterizedTest @MethodSource("badRows")
    void S12_otherBadRowDoesNotHideVpn(String variant) throws Exception {
        badSso(variant);
        assertWire(get("VPN"), VPN, false);
    }
    @ParameterizedTest @MethodSource("badRows")
    void S13_requestedBadRow(String variant) throws Exception {
        badSso(variant);
        assertWire(get("SSO"), INVALID_SSO, true);
    }
    @Test void S14_absentWithReadableBadRow() throws Exception {
        badSso("state-unknown");
        assertWire(get("PAYROLL"), ABSENT, false);
    }
    @ParameterizedTest @ValueSource(strings = {"7", "{}", "{\"id\":7}", "{\"id\":\"\"}", "{\"id\":\"  \"}"})
    void S15_unreadableIdPreventsAbsence(String extra) throws Exception {
        var rows = data().add(StatusWire.JSON.readTree(extra));
        write(directory, rows);
        assertWire(get("PAYROLL"), INVALID_SSO.replace("SSO", "PAYROLL"), true);
    }
    @Test void S16_unreadableIdDoesNotHideVpn() throws Exception {
        write(directory, data().add(7));
        assertWire(get("VPN"), VPN, false);
    }
    @Test void S17_duplicateRequestedId() throws Exception {
        var rows = data(); rows.add(rows.get(1).deepCopy()); write(directory, rows);
        assertWire(get("SSO"), INVALID_SSO, true);
    }
    @Test void S18_otherDuplicateDoesNotHideVpn() throws Exception {
        var rows = data(); rows.add(rows.get(1).deepCopy()); write(directory, rows);
        assertWire(get("VPN"), VPN, false);
    }
    @Test void S19_unknownRowField() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(0)).put("future", "value"); write(directory, rows);
        assertWire(get("VPN"), VPN, false);
    }
    @Test void S20_emptyArray() throws Exception {
        write(directory, List.of());
        assertWire(get("VPN"), ABSENT.replace("PAYROLL", "VPN"), false);
    }
    @Test void S21_rereadsStateAndRevision() throws Exception {
        assertWire(get("VPN"), VPN, false);
        var rows = data(); ((ObjectNode) rows.get(0)).put("state", "incident").put("revision", 2); write(directory, rows);
        assertWire(get("VPN"), VPN.replace("normal", "incident").replace("\"revision\":1", "\"revision\":2"), false);
    }
    @Test void S22_argumentsBeforeFile() throws Exception {
        Files.delete(directory.resolve("services.json"));
        assertWire(get(""), INVALID, true);
    }

    @Test void idWhitespaceIsPreservedAndZeroRevisionIsValid() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(0)).put("id", " VPN").put("revision", 0); write(directory, rows);
        assertWire(get("VPN"), ABSENT.replace("PAYROLL", "VPN"), false);
        assertWire(get(" VPN"), VPN.replace("\"VPN\"", "\" VPN\"").replace("\"revision\":1", "\"revision\":0"), false);
    }

    @Test void declarationDescribesEveryOutcomeAndRejectsBrokenErrors() throws Exception {
        var tools = client.listTools().tools();
        assertEquals(1, tools.size());
        Tool tool = tools.get(0);
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
