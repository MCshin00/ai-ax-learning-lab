package lab.inquiry.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 앱 코드가 호출하는 순차 조회 클라이언트. 연결 실패도 서비스 ID와 함께 반환한다. */
public final class StatusClient implements AutoCloseable {
    private final ServerParameters server;
    private final Duration timeout;
    private McpSyncClient client;
    private boolean closed;

    public StatusClient(Path dataDirectory) {
        this(serverParameters(dataDirectory, System.getProperty("java.class.path")), Duration.ofSeconds(10));
    }

    StatusClient(ServerParameters server, Duration timeout) {
        this.server = Objects.requireNonNull(server);
        this.timeout = Objects.requireNonNull(timeout);
    }

    static ServerParameters serverParameters(Path directory, String classpath) {
        return ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin", "java").toString())
                .args("-Dfile.encoding=UTF-8", "-cp", classpath, StatusServer.class.getName(),
                        directory.toAbsolutePath().normalize().toString()).build();
    }

    private McpSyncClient connection() {
        if (closed) throw new IllegalStateException("종료한 연결입니다.");
        if (client == null) {
            client = McpClient.sync(new StdioClientTransport(server, McpJsonDefaults.getMapper()))
                    .requestTimeout(timeout).initializationTimeout(timeout).build();
            try {
                client.initialize();
            } catch (RuntimeException error) {
                disconnect();
                throw error;
            }
        }
        return client;
    }

    public StatusResult get(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) return StatusResult.invalid(serviceId);
        CallToolResult response;
        try {
            response = connection().callTool(new CallToolRequest(StatusServer.TOOL_NAME, Map.of("serviceId", serviceId)));
        } catch (RuntimeException error) {
            disconnect();
            return StatusResult.unavailable(serviceId, StatusResult.ErrorCode.MCP_UNAVAILABLE,
                    "MCP 서버와 통신할 수 없습니다.");
        }
        return decode(serviceId, response);
    }

    static StatusResult decode(String serviceId, CallToolResult response) {
        try {
            StatusResult result = StatusJson.MAPPER.convertValue(response.structuredContent(), StatusResult.class);
            if (result == null || !serviceId.equals(result.serviceId())
                    || result.isError() != Boolean.TRUE.equals(response.isError())) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException error) {
            return StatusResult.unavailable(serviceId, StatusResult.ErrorCode.INVALID_RESPONSE,
                    "MCP 조회 응답이 약속한 형식과 다릅니다.");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolListing(List<Tool> tools, StatusResult.ErrorCode code, String message) {}

    public ToolListing listTools() {
        try {
            return new ToolListing(List.copyOf(connection().listTools().tools()), null, null);
        } catch (RuntimeException error) {
            disconnect();
            return new ToolListing(List.of(), StatusResult.ErrorCode.MCP_UNAVAILABLE,
                    "MCP 도구 목록을 조회할 수 없습니다.");
        }
    }

    private void disconnect() {
        McpSyncClient previous = client;
        client = null;
        if (previous != null) {
            try { previous.closeGracefully(); }
            catch (RuntimeException ignored) { /* 종료 실패가 원래 조회 결과를 덮어쓰지 않게 한다. */ }
        }
    }

    @Override public void close() {
        closed = true;
        disconnect();
    }
}
