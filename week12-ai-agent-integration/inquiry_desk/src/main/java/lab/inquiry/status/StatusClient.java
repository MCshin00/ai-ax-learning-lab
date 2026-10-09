package lab.inquiry.status;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static lab.inquiry.status.LookupResult.Cause.*;

/** 순서대로 호출하는 앱 클라이언트. 실패도 해당 서비스의 값으로 돌려준다. */
public final class StatusClient implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final ServerParameters server;
    private McpSyncClient connection;

    public StatusClient(Path directory) {
        this(serverParameters(directory, System.getProperty("java.class.path")));
    }

    StatusClient(ServerParameters server) { this.server = server; }

    static ServerParameters serverParameters(Path directory, String classpath) {
        return ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .args("-Dfile.encoding=UTF-8", "-cp", classpath, StatusServerMain.class.getName(),
                        directory.toAbsolutePath().toString()).build();
    }

    private McpSyncClient connect() {
        if (connection == null) {
            connection = McpClient.sync(new StdioClientTransport(server, McpJsonDefaults.getMapper()))
                    .requestTimeout(TIMEOUT).initializationTimeout(TIMEOUT)
                    .build();
            connection.initialize();
        }
        return connection;
    }

    public LookupResult get(String serviceId) {
        try {
            var response = connect().callTool(new CallToolRequest(StatusWire.TOOL_NAME,
                    Collections.singletonMap("serviceId", serviceId)));
            return StatusWire.decode(serviceId, response);
        } catch (RuntimeException e) {
            disconnect();
            return LookupResult.failed(serviceId, MCP_UNAVAILABLE);
        }
    }

    public record ToolList(List<Tool> tools, LookupResult failure) {}

    public ToolList listTools() {
        try { return new ToolList(List.copyOf(connect().listTools().tools()), null); }
        catch (RuntimeException e) {
            disconnect();
            return new ToolList(List.of(), LookupResult.failed(null, MCP_UNAVAILABLE));
        }
    }

    private void disconnect() {
        var previous = connection;
        connection = null;
        if (previous != null) {
            previous.closeGracefully();
        }
    }

    @Override public void close() { disconnect(); }
}
