package lab.inquiry.status;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.nio.file.Path;

/** 표준 출력은 MCP 전송 전용이다. */
public final class StatusServerMain {
    private StatusServerMain() {}

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && args[0].isEmpty())) {
            System.err.println("사용법: StatusServerMain [자료 폴더]");
            System.exit(2);
            return;
        }
        var catalog = new ServiceCatalog(Path.of(args.length == 0 ? "data" : args[0]));
        var tool = SyncToolSpecification.builder().tool(StatusWire.tool())
                .callHandler((exchange, request) -> StatusWire.encode(catalog.lookup(request.arguments()))).build();
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
                .serverInfo("inquiry-status", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build()).tools(tool).build();
    }
}
