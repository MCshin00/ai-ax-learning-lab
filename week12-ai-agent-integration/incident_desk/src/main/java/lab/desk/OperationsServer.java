package lab.desk;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/** 업무 기능을 MCP로 공개합니다. 모델과 대화 상태는 이 서버에 없습니다. */
public final class OperationsServer {
    private OperationsServer() {}
    private static SyncToolSpecification tool(String name,String description,Map<String,Object> properties,
                                              Function<Map<String,Object>,Object> handler) {
        var schema=Map.of("type","object","properties",properties,"required",List.copyOf(properties.keySet()),"additionalProperties",false);
        var definition=Tool.builder().name(name).description(description)
            .inputSchema(McpJsonDefaults.getMapper(),Json.write(schema)).build();
        return SyncToolSpecification.builder().tool(definition).callHandler((exchange,request) -> {
            try {
                var value=Json.MAPPER.convertValue(handler.apply(request.arguments()),Object.class);
                return CallToolResult.builder().content(List.of(new TextContent(Json.write(value))))
                    .structuredContent(value).isError(false).build();
            } catch (RuntimeException e) {
                return CallToolResult.builder().content(List.of(new TextContent("업무 입력 또는 자료 접근을 확인하세요.")))
                    .isError(true).build();
            }
        }).build();
    }
    public static void main(String[] args) {
        Path data=Path.of(args.length>0?args[0]:"data"),drafts=Path.of(args.length>1?args[1]:".local/requests");
        var service=new OperationsStore(data,drafts);
        var string=Map.<String,Object>of("type","string");
        var tools=List.of(
            tool("get_service_status","서비스 ID로 현재 공통 상태를 조회합니다.",Map.of("serviceId",string),
                input -> service.lookup((String)input.get("serviceId"))),
            tool("save_work_request","사람이 확인한 작업 요청을 저장합니다. 같은 요청 ID와 내용은 같은 요청을 반환합니다.",
                Map.of("draft",Map.of("type","object")),input -> service.save(Json.tree(input.get("draft")))),
            tool("read_work_request","저장된 작업 요청을 ID로 조회합니다.",Map.of("id",string),
                input -> service.read((String)input.get("id"))));
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
            .serverInfo("it-operations","1.0.0")
            .capabilities(ServerCapabilities.builder().tools(false).build()).tools(tools).build();
    }
}
