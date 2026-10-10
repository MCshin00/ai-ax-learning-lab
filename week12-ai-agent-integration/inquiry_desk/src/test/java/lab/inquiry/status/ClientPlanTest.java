package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static lab.inquiry.status.LookupResult.Outcome.*;
import static lab.inquiry.status.PlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientPlanTest {
    @TempDir Path directory;

    private static ObjectNode node(String text) throws Exception { return (ObjectNode) StatusWire.JSON.readTree(text); }
    private static CallToolResult response(Object value, Boolean error) {
        return new CallToolResult(List.of(new TextContent("텍스트는 해석하지 않는다.")), error, value, null);
    }
    private static LookupResult read(Object value, Boolean error) { return StatusWire.decode("VPN", response(value, error)); }
    private static void invalidResponse(LookupResult result) {
        assertEquals(UNAVAILABLE, result.outcome());
        assertEquals("INVALID_RESPONSE", result.code());
        assertEquals("VPN", result.serviceId());
        assertEquals("상태 조회 서버의 응답이 약속한 형식과 다릅니다.", result.message());
    }

    @Test void C2_realProcessDoesNotAnswerWithinTenSeconds() {
        try (var client = new StatusClient(fixture())) {
            assertNull(client.listTools().failure()); // 초기화가 끝난 뒤 조회 응답만 지연한다.
            long start = System.nanoTime();
            var result = client.get("VPN");
            long elapsed = java.time.Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertEquals(UNAVAILABLE, result.outcome());
            assertEquals("MCP_UNAVAILABLE", result.code());
            assertEquals("VPN", result.serviceId());
            assertTrue(elapsed >= 9_500 && elapsed < 20_000, "10초 응답 제한: " + elapsed);
        }
    }
    @Test void C3_ignoresUnknownField() throws Exception {
        var value = node(VPN).put("future", "new");
        assertEquals(VPN, StatusWire.text(StatusWire.fields(read(value, false))));
    }

    @Test void C4_missingRequiredField() throws Exception {
        var value = node(VPN); value.remove("state");
        invalidResponse(read(value, false));
    }
    @Test void C8_unknownOutcome() throws Exception { invalidResponse(read(node(VPN).put("outcome", "FUTURE"), false)); }
    @Test void C9_noStructuredObjectEvenWithValidText() {
        invalidResponse(StatusWire.decode("VPN", new CallToolResult(List.of(new TextContent(VPN)), false, null, null)));
    }
    @Test void C12_failedVpnDoesNotEraseMailResult() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(0)).remove("detail"); write(directory, rows);
        try (var client = new StatusClient(server(directory))) {
            var vpn = client.get("VPN");
            var mail = client.get("MAIL");
            assertEquals(UNAVAILABLE, vpn.outcome()); assertEquals("DATA_INVALID", vpn.code());
            assertEquals("VPN", vpn.serviceId());
            assertEquals(LookupResult.found("MAIL", "normal", "현재 공통 장애 공지는 없다.", 1), mail);
        }
    }
    @Test void C13_serverInvalidArgumentsPreserved() throws Exception {
        var result = read(node(INVALID), true);
        assertEquals(INVALID_INPUT, result.outcome()); assertEquals("INVALID_ARGUMENTS", result.code());
        assertEquals(INVALID, StatusWire.text(StatusWire.fields(result)));
    }
    @Test void C16_dataRulesBelongToServer() throws Exception {
        var value = node(VPN);
        value.put("state", "future");
        assertEquals(StatusWire.text(value), StatusWire.text(StatusWire.fields(read(value, false))));
    }
}
