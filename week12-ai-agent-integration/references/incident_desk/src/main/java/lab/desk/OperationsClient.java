package lab.desk;

import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** 화면 앱과 독립 운영 도구에서 같은 MCP 업무 기능을 사용합니다. */
public final class OperationsClient implements Operations, AutoCloseable {
    private final McpSyncClient client;
    public OperationsClient(Path data,Path drafts) {
        var java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        var server=ServerParameters.builder(java).args("-Dfile.encoding=UTF-8","-cp",
            System.getProperty("server.classpath",System.getProperty("java.class.path")),
            "lab.desk.OperationsServer",data.toAbsolutePath().toString(),drafts.toAbsolutePath().toString()).build();
        client=McpClient.sync(new StdioClientTransport(server,McpJsonDefaults.getMapper()))
            .requestTimeout(Duration.ofSeconds(20)).build();
        try {client.initialize();} catch (RuntimeException e) {client.closeGracefully();throw e;}
    }
    public List<Tool> tools() {return client.listTools().tools();}
    public JsonNode call(String name,Map<String,Object> args) {
        // SDK의 JSON 구현에 특정 Jackson JsonNode를 넘기지 않고 일반 Map/List 값으로 전달합니다.
        @SuppressWarnings("unchecked")
        Map<String,Object> arguments=Json.MAPPER.convertValue(args,Map.class);
        var result=client.callTool(new CallToolRequest(name,arguments));
        if (Boolean.TRUE.equals(result.isError())) throw new IllegalArgumentException("MCP 업무 도구가 입력 또는 자료 오류를 반환했습니다.");
        if (result.structuredContent()!=null) return Json.tree(result.structuredContent());
        return Json.read(result.content().stream().filter(TextContent.class::isInstance)
            .map(TextContent.class::cast).map(TextContent::text).findFirst().orElseThrow());
    }
    public Models.Lookup lookup(String id) {return Json.as(call("get_service_status",Map.of("serviceId",id)),Models.Lookup.class);}
    public JsonNode save(JsonNode draft) {return call("save_work_request",Map.of("draft",draft));}
    @Override public void close() {client.closeGracefully();}
    public static void main(String[] args) {
        try (var client=new OperationsClient(Path.of("data"),Path.of(".local/requests"))) {
            if (args.length==0) System.out.println(Json.write(client.tools()));
            else if (args.length==2 && args[0].equals("get")) System.out.println(Json.write(client.lookup(args[1])));
            else if (args.length==2 && args[0].equals("read"))
                System.out.println(Json.write(client.call("read_work_request",Map.of("id",args[1]))));
            else throw new IllegalArgumentException("인수 없이 도구 목록, get 서비스ID, read 저장ID를 사용하세요.");
        }
    }
}
