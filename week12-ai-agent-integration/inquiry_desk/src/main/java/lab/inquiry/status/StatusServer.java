package lab.inquiry.status;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 표준 출력은 MCP 전송에만 사용한다. */
public final class StatusServer {
    public static final String TOOL_NAME = "get_service_status";

    private StatusServer() {}

    static SyncToolSpecification statusTool(Path dataDirectory) {
        var repository = new ServiceStatusRepository(dataDirectory);
        var definition = Tool.builder().name(TOOL_NAME)
                .description("서비스 ID로 현재 상태·설명·변경 번호를 조회합니다. ID는 대소문자를 구별해 그대로 찾습니다. "
                        + "자료에 없으면 정상 NOT_FOUND, 조회 불가와 잘못된 입력은 오류와 code를 반환합니다.")
                .inputSchema(new JsonSchema("object", Map.of("serviceId", Map.of(
                        "type", "string", "minLength", 1, "pattern", "\\S",
                        "description", "조회할 서비스 ID. 예: VPN, SSO, MAIL. 자료에 없는 ID도 조회할 수 있습니다.")),
                        List.of("serviceId"), false, null, null))
                .annotations(new ToolAnnotations(null, true, false, true, false, null)).build();
        return SyncToolSpecification.builder().tool(definition).callHandler((exchange, request) -> {
            Map<String, Object> input = request.arguments();
            Object id = input == null ? null : input.get("serviceId");
            StatusResult result = input != null && input.size() == 1 && id instanceof String text
                    ? repository.get(text) : StatusResult.invalid(id instanceof String text ? text : null);
            // 텍스트와 구조화된 내용에 같은 값을 담아 어느 소비자에게도 의미가 같게 한다.
            var value = StatusJson.MAPPER.convertValue(result, Map.class);
            return CallToolResult.builder().isError(result.isError()).structuredContent(value)
                    .content(List.of(new TextContent(StatusJson.text(value)))).build();
        }).build();
    }

    public static void main(String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("인수: [자료 폴더]");
        Path directory = args.length == 0 ? Path.of("data") : Path.of(args[0]);
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
                .serverInfo("inquiry-status", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(statusTool(directory)).build();
    }
}
