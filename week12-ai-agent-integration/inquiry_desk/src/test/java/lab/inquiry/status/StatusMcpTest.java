package lab.inquiry.status;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static lab.inquiry.status.StatusResult.ErrorCode.*;
import static lab.inquiry.status.StatusResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class StatusMcpTest {
    @TempDir Path temporaryData;

    private ServerParameters parameters(Path directory) {
        return StatusClient.serverParameters(directory, System.getProperty("inquiry.server.classpath"));
    }

    private McpSyncClient connect(Path directory) {
        var client = McpClient.sync(new StdioClientTransport(parameters(directory), McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(10)).initializationTimeout(Duration.ofSeconds(10)).build();
        try { client.initialize(); }
        catch (RuntimeException error) { client.closeGracefully(); throw error; }
        return client;
    }

    private CallToolResult call(McpSyncClient client, Map<String, Object> input) {
        return client.callTool(new CallToolRequest(StatusServer.TOOL_NAME, input));
    }

    @Test void publishesTheStatusToolAndRequiredServiceIdSchema() {
        try (var client = connect(Path.of("data"))) {
            var tools = client.listTools().tools();
            assertEquals(1, tools.size());
            var tool = tools.get(0);
            assertEquals("get_service_status", tool.name());
            assertEquals(List.of("serviceId"), tool.inputSchema().required());
            assertEquals("string", ((Map<?, ?>) tool.inputSchema().properties().get("serviceId")).get("type"));
            assertEquals(false, tool.inputSchema().additionalProperties());
            assertFalse(tool.description().isBlank());
        }
    }

    @Test void vpnMatchesTheProvidedDataAndPayrollIsSuccessfulAbsence() throws Exception {
        var rows = StatusJson.MAPPER.readTree(Files.readString(Path.of("data/services.json")));
        var expected = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> row.path("id").asText().equals("VPN")).findFirst().orElseThrow();
        try (var client = connect(Path.of("data"))) {
            var response = call(client, Map.of("serviceId", "VPN"));
            assertEquals(false, response.isError());
            var vpn = StatusClient.decode("VPN", response);
            assertEquals(FOUND, vpn.outcome());
            assertEquals(expected.path("state").asText(), vpn.state());
            assertEquals(expected.path("detail").asText(), vpn.detail());
            assertEquals(expected.path("revision").longValue(), vpn.revision());
            var text = ((TextContent) response.content().get(0)).text();
            assertEquals(StatusJson.MAPPER.valueToTree(response.structuredContent()), StatusJson.MAPPER.readTree(text));

            var absent = call(client, Map.of("serviceId", "PAYROLL"));
            assertEquals(false, absent.isError());
            assertEquals(StatusResult.notFound("PAYROLL"), StatusClient.decode("PAYROLL", absent));
        }
    }

    @Test void missingDataIsAToolErrorWithAMachineReadableCause() {
        try (var client = connect(temporaryData)) {
            var response = call(client, Map.of("serviceId", "VPN"));
            assertEquals(true, response.isError());
            var result = StatusClient.decode("VPN", response);
            assertEquals(UNAVAILABLE, result.outcome());
            assertEquals(DATA_UNREADABLE, result.code());
            assertNull(result.state());
            assertFalse(result.message().contains(temporaryData.toString()));
        }
    }

    @Test void malformedDataIsNotReportedAsAbsence() throws Exception {
        Files.writeString(temporaryData.resolve("services.json"), "{잘못된 자료");
        try (var client = connect(temporaryData)) {
            var response = call(client, Map.of("serviceId", "PAYROLL"));
            assertEquals(true, response.isError());
            assertEquals(DATA_INVALID, StatusClient.decode("PAYROLL", response).code());
        }
    }

    @Test void missingBlankWrongTypeAndExtraArgumentsHaveAnExplicitInputError() {
        try (var client = connect(Path.of("data"))) {
            List<Map<String, Object>> inputs = List.of(Map.of(), Map.of("serviceId", ""),
                    Map.of("serviceId", "  "), Map.of("serviceId", 7),
                    Map.of("serviceId", "VPN", "extra", true));
            for (var input : inputs) {
                var response = call(client, input);
                assertEquals(true, response.isError(), input.toString());
                var result = StatusJson.MAPPER.convertValue(response.structuredContent(), StatusResult.class);
                assertEquals(INVALID_INPUT, result.outcome());
                assertEquals(INVALID_ARGUMENTS, result.code());
            }
        }
    }

    @Test void clientKeepsEachResultAndReadsChangedDataOnTheNextCall() throws Exception {
        Path file = temporaryData.resolve("services.json");
        Files.copy(Path.of("data/services.json"), file);
        try (var client = new StatusClient(parameters(temporaryData), Duration.ofSeconds(10))) {
            var vpn = client.get("VPN");
            var payroll = client.get("PAYROLL");
            assertEquals(FOUND, vpn.outcome());
            assertEquals(NOT_FOUND, payroll.outcome());
            Files.delete(file);
            assertEquals(DATA_UNREADABLE, client.get("SSO").code());
            assertEquals("VPN", vpn.serviceId());
            assertEquals(1L, vpn.revision());
            Files.writeString(file, "[{\"id\":\"VPN\",\"state\":\"incident\",\"detail\":\"변경된 공지\",\"revision\":2}]");
            assertEquals(StatusResult.found("VPN", "incident", "변경된 공지", 2), client.get("VPN"));
            assertNull(client.listTools().code());
        }
    }

    @Test void aServerThatCannotStartReturnsConnectionFailureAsAValue() {
        var missingServer = ServerParameters.builder(temporaryData.resolve("missing-java").toString()).build();
        try (var client = new StatusClient(missingServer, Duration.ofSeconds(2))) {
            assertEquals(INVALID_INPUT, client.get(" ").outcome());
            var result = client.get("VPN");
            assertEquals(UNAVAILABLE, result.outcome());
            assertEquals(MCP_UNAVAILABLE, result.code());
            assertEquals("VPN", result.serviceId());
            assertEquals(MCP_UNAVAILABLE, client.listTools().code());
        }
    }

    @Test void rejectsResponsesForAnotherServiceOrWithInconsistentErrorFlags() {
        var other = CallToolResult.builder().structuredContent(StatusResult.notFound("SSO")).isError(false).build();
        assertEquals(INVALID_RESPONSE, StatusClient.decode("VPN", other).code());
        var wrongFlag = CallToolResult.builder().structuredContent(StatusResult.notFound("VPN")).isError(true).build();
        assertEquals(INVALID_RESPONSE, StatusClient.decode("VPN", wrongFlag).code());
    }

    @Test void missingCliArgumentsReturnInputErrorWithoutStartingAServer() {
        for (String[] args : List.of(new String[]{"get"}, new String[]{"get", " "},
                new String[]{"unknown"}, new String[]{"--data-dir"})) {
            var result = (StatusResult) OperationsClient.execute(args);
            assertEquals(INVALID_INPUT, result.outcome());
            assertEquals(INVALID_ARGUMENTS, result.code());
        }
    }
}
