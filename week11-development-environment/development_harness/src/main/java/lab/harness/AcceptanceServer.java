package lab.harness;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import java.nio.file.Path;
import java.util.*;

/** 개발할 기능의 입력 예와 기대 결과를 공개합니다. */
public final class AcceptanceServer {
    static Object lookup(Path project,String id) throws Exception {
        for(var entry:HarnessMain.JSON.readTree(project.resolve("data/acceptance-cases.json").toFile()))
            if(entry.path("id").asText().equals(id)) return HarnessMain.JSON.convertValue(entry,Object.class);
        throw new IllegalArgumentException("unknown_case");
    }
    static void start(Path project) {
        var schema="{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},\"required\":[\"id\"],\"additionalProperties\":false}";
        var tool=Tool.builder().name("get_acceptance_case").description("개발할 기능의 입력 예·기대 결과·판단 이유를 ID로 조회합니다.")
            .inputSchema(McpJsonDefaults.getMapper(),schema).build();
        var specification=SyncToolSpecification.builder().tool(tool).callHandler((exchange,request)->{
            try {
                Object result=lookup(project,(String)request.arguments().get("id"));
                return CallToolResult.builder().content(List.of(new TextContent(HarnessMain.JSON.writeValueAsString(result))))
                    .structuredContent(result).isError(false).build();
            } catch(Exception e) {
                return CallToolResult.builder().content(List.of(new TextContent("요구 ID와 사례 자료를 확인하세요."))).isError(true).build();
            }
        }).build();
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
            .serverInfo("development-acceptance","1.0.0").capabilities(ServerCapabilities.builder().tools(false).build())
            .tools(List.of(specification)).build();
    }
}
