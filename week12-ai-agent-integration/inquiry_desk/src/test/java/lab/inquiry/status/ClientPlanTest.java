package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static lab.inquiry.status.LookupResult.Cause.*;
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
        assertEquals(INVALID_RESPONSE, result.code());
        assertEquals("VPN", result.serviceId());
        assertEquals("상태 조회 서버의 응답이 약속한 형식과 다릅니다.", result.message());
    }

    @Test void C1_processCannotStart() {
        try (var client = new StatusClient(ServerParameters.builder(directory.resolve("missing-java").toString()).build())) {
            var result = client.get("VPN");
            assertEquals(UNAVAILABLE, result.outcome());
            assertEquals(MCP_UNAVAILABLE, result.code());
            assertEquals("VPN", result.serviceId());
            assertEquals("상태 조회 서버와 통신하지 못했습니다.", result.message());
        }
    }
    @Test void C2_realProcessDoesNotAnswerWithinTenSeconds() {
        try (var client = new StatusClient(fixture("stall"))) {
            assertNull(client.listTools().failure()); // 초기화가 끝난 뒤 조회 응답만 지연한다.
            long start = System.nanoTime();
            var result = client.get("VPN");
            long elapsed = java.time.Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertEquals(UNAVAILABLE, result.outcome());
            assertEquals(MCP_UNAVAILABLE, result.code());
            assertEquals("VPN", result.serviceId());
            assertTrue(elapsed >= 9_500 && elapsed < 20_000, "10초 응답 제한: " + elapsed);
        }
    }
    @Test void C3_ignoresUnknownField() throws Exception {
        var value = node(VPN).put("future", "new");
        assertEquals(VPN, StatusWire.text(StatusWire.fields(read(value, false))));
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of(VPN, false, "outcome"), Arguments.of(VPN, false, "serviceId"),
                Arguments.of(VPN, false, "state"), Arguments.of(VPN, false, "detail"), Arguments.of(VPN, false, "revision"),
                Arguments.of(ABSENT.replace("PAYROLL", "VPN"), false, "serviceId"),
                Arguments.of(UNREADABLE, true, "serviceId"), Arguments.of(UNREADABLE, true, "code"),
                Arguments.of(UNREADABLE, true, "message"), Arguments.of(INVALID, true, "code"),
                Arguments.of(INVALID, true, "message"));
    }
    @ParameterizedTest @MethodSource("requiredFields")
    void C4_missingRequiredField(String example, boolean error, String field) throws Exception {
        var value = node(example); value.remove(field);
        invalidResponse(read(value, error));
    }
    @ParameterizedTest @ValueSource(strings = {"revision", "state"})
    void C5_wrongType(String field) throws Exception {
        var value = node(VPN);
        if (field.equals("revision")) value.put(field, "1"); else value.put(field, 7);
        invalidResponse(read(value, false));
    }
    @Test void C6_otherService() throws Exception { invalidResponse(read(node(VPN).put("serviceId", "SSO"), false)); }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void C7_errorFlagMismatch(boolean error) throws Exception {
        invalidResponse(read(node(error ? VPN : UNREADABLE), error));
    }
    @Test void C8_unknownOutcome() throws Exception { invalidResponse(read(node(VPN).put("outcome", "FUTURE"), false)); }

    static Stream<Arguments> nonObjects() { return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()), Arguments.of("text"), Arguments.of(7)); }
    @ParameterizedTest @MethodSource("nonObjects")
    void C9_noStructuredObjectEvenWithValidText(Object structured) {
        invalidResponse(StatusWire.decode("VPN", new CallToolResult(List.of(new TextContent(VPN)), false, structured, null)));
    }
    @Test void C10_structuredContentWins() throws Exception {
        var response = new CallToolResult(List.of(new TextContent(ABSENT)), false, node(VPN), null);
        assertEquals(VPN, StatusWire.text(StatusWire.fields(StatusWire.decode("VPN", response))));
    }
    @Test void C11_unknownUnavailableCauseHasExactShape() throws Exception {
        var result = read(node(UNREADABLE).put("code", "RATE_LIMITED"), true);
        assertEquals(UNAVAILABLE, result.outcome());
        assertEquals(LookupResult.Cause.UNKNOWN, result.code());
        assertEquals("RATE_LIMITED", result.receivedCode());
        assertEquals(PlanSupport.UNKNOWN, StatusWire.text(StatusWire.fields(result)));
    }
    @Test void C12_failedVpnDoesNotEraseMailResult() throws Exception {
        var rows = data(); ((ObjectNode) rows.get(0)).put("state", "invalid"); write(directory, rows);
        try (var client = new StatusClient(server(directory))) {
            var vpn = client.get("VPN");
            var mail = client.get("MAIL");
            assertEquals(UNAVAILABLE, vpn.outcome()); assertEquals(DATA_INVALID, vpn.code());
            assertEquals("VPN", vpn.serviceId());
            assertEquals(LookupResult.found("MAIL", "normal", "현재 공통 장애 공지는 없다.", 1), mail);
        }
    }
    @Test void C13_serverInvalidArgumentsPreserved() throws Exception {
        var result = read(node(INVALID), true);
        assertEquals(INVALID_INPUT, result.outcome()); assertEquals(INVALID_ARGUMENTS, result.code());
        assertEquals(INVALID, StatusWire.text(StatusWire.fields(result)));
    }
    @Test void C14_unknownInvalidInputCausePreservesOutcomeAndRequestedId() throws Exception {
        var result = read(node(INVALID).put("code", "RATE_LIMITED"), true);
        assertEquals(INVALID_INPUT, result.outcome()); assertEquals(LookupResult.Cause.UNKNOWN, result.code());
        assertEquals("VPN", result.serviceId()); assertEquals("RATE_LIMITED", result.receivedCode());
        assertEquals(PlanSupport.UNKNOWN.replace("UNAVAILABLE", "INVALID_INPUT"), StatusWire.text(StatusWire.fields(result)));
    }
    @Test void C15_otherServiceBeforeUnknownCause() throws Exception {
        invalidResponse(read(node(UNREADABLE).put("serviceId", "SSO").put("code", "RATE_LIMITED"), true));
    }
    @ParameterizedTest @ValueSource(strings = {"state", "revision"})
    void C16_dataRulesBelongToServer(String field) throws Exception {
        var value = node(VPN);
        if (field.equals("state")) value.put("state", "future"); else value.put("revision", -1);
        assertEquals(StatusWire.text(value), StatusWire.text(StatusWire.fields(read(value, false))));
    }
    @Test void C17_irrelevantFieldIgnored() throws Exception {
        var value = node(ABSENT.replace("PAYROLL", "VPN")).put("state", "normal");
        assertEquals(LookupResult.absent("VPN"), read(value, false));
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void C18_knownCauseOnWrongOutcome(boolean invalidInput) throws Exception {
        var value = node(invalidInput ? INVALID : UNREADABLE).put("code", invalidInput ? "DATA_INVALID" : "INVALID_ARGUMENTS");
        invalidResponse(read(value, true));
    }
    @Test void absentErrorFlagMeansFalse() throws Exception {
        assertEquals(VPN, StatusWire.text(StatusWire.fields(read(node(VPN), null))));
        invalidResponse(read(node(UNREADABLE), null));
    }
}
