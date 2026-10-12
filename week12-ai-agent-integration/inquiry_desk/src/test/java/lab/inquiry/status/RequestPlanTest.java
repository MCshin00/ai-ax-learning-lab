package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static lab.inquiry.status.PlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestPlanTest {
    private static final String REVIEW = "7b1c0c1e-0000-4000-8000-000000000001";
    private static final RequestData DATA = new RequestData(REVIEW, " 담당자가 고친 본문\n",
            List.of(new RequestData.Service("VPN", 1)), List.of("RB-VPN"),
            List.of(new RequestData.Unprocessed("급여 시스템", "NOT_FOUND")));
    private static final String SAVED = "{\"outcome\":\"SAVED\",\"requestId\":\"REQ-0001\"}";
    private static final String FOUND = "{\"outcome\":\"FOUND\",\"requestId\":\"REQ-0001\",\"reviewId\":\"" + REVIEW
            + "\",\"text\":\" 담당자가 고친 본문\\n\",\"services\":[{\"serviceId\":\"VPN\",\"revision\":1}],\"sources\":[\"RB-VPN\"],"
            + "\"unprocessed\":[{\"serviceId\":\"급여 시스템\",\"code\":\"NOT_FOUND\"}]}";
    private static final String ABSENT_REQUEST = "{\"outcome\":\"NOT_FOUND\",\"requestId\":\"REQ-0009\"}";
    private static final String CONFLICT = "{\"outcome\":\"CONFLICT\",\"code\":\"CONTENT_MISMATCH\",\"message\":\"같은 검토본으로 다른 내용이 이미 저장되어 있습니다.\"}";
    private static final String CHANGED = "{\"outcome\":\"STATUS_CHANGED\",\"serviceId\":\"VPN\",\"code\":\"REVISION_MISMATCH\",\"message\":\"검토한 뒤 서비스 상태가 바뀌었습니다. 다시 문의해 검토해 주세요.\"}";
    private static final String INVALID_SAVE = "{\"outcome\":\"INVALID_INPUT\",\"code\":\"INVALID_ARGUMENTS\",\"message\":\"reviewId와 text는 공백이 아닌 문자열이고 services, sources, unprocessed는 선언된 배열이어야 하며 다른 인수는 받지 않습니다.\"}";
    private static final String UNREADABLE_REQUEST = "{\"outcome\":\"UNAVAILABLE\",\"code\":\"REQUESTS_UNREADABLE\",\"message\":\"저장된 요청을 읽을 수 없습니다.\"}";
    @TempDir static Path directory;
    private McpSyncClient client;

    @BeforeAll void start() { client = connect(server(directory)); }
    @AfterAll void stop() { client.closeGracefully(); }
    @BeforeEach void reset() throws Exception {
        write(directory, data());
        Files.deleteIfExists(directory.resolve("requests.json"));
    }

    private CallToolResult save() { return call("save", RequestWire.arguments(DATA)); }
    private CallToolResult get(String id) { return call("get", Map.of("requestId", id)); }
    private CallToolResult call(String operation, Map<String, Object> arguments) {
        return client.callTool(new CallToolRequest(operation + "_request", arguments));
    }
    private static void wire(CallToolResult result, String expected, boolean error) {
        assertEquals(error, result.isError());
        assertEquals(expected, StatusWire.text(result.structuredContent()));
        assertEquals(expected, StatusWire.text(RequestWire.fields(RequestWire.decode(result))));
    }

    @Test void saveThenRead() {
        wire(save(), SAVED, false);
        wire(get("REQ-0001"), FOUND, false);
    }

    @Test void readAfterServerRestart() {
        wire(save(), SAVED, false);
        client.closeGracefully();
        client = connect(server(directory));
        wire(get("REQ-0001"), FOUND, false);
    }

    @Test void retransmissionKeepsOneRequest() throws Exception {
        wire(save(), SAVED, false);
        wire(save(), SAVED, false);
        assertEquals(1, StatusWire.JSON.readTree(Files.readString(directory.resolve("requests.json"))).size());
    }

    @Test void differentTextConflicts() {
        save();
        var changed = new LinkedHashMap<>(RequestWire.arguments(DATA));
        changed.put("text", "바꾼 본문");
        wire(call("save", changed), CONFLICT, true);
        wire(get("REQ-0001"), FOUND, false);
    }

    @Test void changedRevisionDoesNotSave() throws Exception {
        var rows = data();
        ((ObjectNode) rows.get(0)).put("revision", 2);
        write(directory, rows);
        wire(save(), CHANGED, true);
        assertFalse(Files.exists(directory.resolve("requests.json")));
    }

    @Test void absentRequestIsNormal() { wire(get("REQ-0009"), ABSENT_REQUEST, false); }

    @Test void missingTextIsInvalid() {
        var arguments = new LinkedHashMap<>(RequestWire.arguments(DATA));
        arguments.remove("text");
        wire(call("save", arguments), INVALID_SAVE, true);
    }

    @Test void unreadableRequests() throws Exception {
        Files.writeString(directory.resolve("requests.json"), "not json");
        wire(get("REQ-0001"), UNREADABLE_REQUEST, true);
    }

    @Test void threeToolsDeclareAllOutcomes() throws Exception {
        var tools = client.listTools().tools();
        assertEquals(3, tools.size());
        for (String operation : List.of("save", "get")) {
            var tool = tools.stream().filter(item -> item.name().equals(operation + "_request")).findFirst().orElseThrow();
            assertEquals(RequestWire.tool(operation).inputSchema(), tool.inputSchema());
            assertEquals(RequestWire.schema(operation + "-request-output"), tool.outputSchema());
            var examples = operation.equals("save") ? List.of(SAVED, CONFLICT, CHANGED, INVALID_SAVE, UNREADABLE_REQUEST)
                    : List.of(FOUND, ABSENT_REQUEST, INVALID_SAVE, UNREADABLE_REQUEST);
            for (String example : examples) {
                var value = StatusWire.JSON.readValue(example, Map.class);
                assertTrue(McpJsonDefaults.getSchemaValidator().validate(tool.outputSchema(), value).valid(), example);
            }
        }
    }

    @Test void operationsReadsStoredAndAbsentRequest() throws Exception {
        save();
        var found = runMain(OperationsMain.class, "--data-dir", directory.toString(), "request", "REQ-0001");
        assertEquals(0, found.exit(), found.err());
        assertEquals(FOUND + System.lineSeparator(), found.out());
        var absent = runMain(OperationsMain.class, "--data-dir", directory.toString(), "request", "REQ-0009");
        assertEquals(0, absent.exit(), absent.err());
        assertEquals(ABSENT_REQUEST + System.lineSeparator(), absent.out());
    }
}
