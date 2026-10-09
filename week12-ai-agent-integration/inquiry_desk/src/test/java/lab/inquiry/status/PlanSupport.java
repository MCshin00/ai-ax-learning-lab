package lab.inquiry.status;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class PlanSupport {
    static final String VPN = "{\"outcome\":\"FOUND\",\"serviceId\":\"VPN\",\"state\":\"normal\",\"detail\":\"현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.\",\"revision\":1}";
    static final String ABSENT = "{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"PAYROLL\"}";
    static final String UNREADABLE = "{\"outcome\":\"UNAVAILABLE\",\"serviceId\":\"VPN\",\"code\":\"DATA_UNREADABLE\",\"message\":\"서비스 자료를 읽을 수 없습니다.\"}";
    static final String INVALID = "{\"outcome\":\"INVALID_INPUT\",\"code\":\"INVALID_ARGUMENTS\",\"message\":\"serviceId는 공백이 아닌 문자열 하나여야 하고 다른 인수는 받지 않습니다.\"}";
    static final String INVALID_SSO = "{\"outcome\":\"UNAVAILABLE\",\"serviceId\":\"SSO\",\"code\":\"DATA_INVALID\",\"message\":\"서비스 자료의 형식이 올바르지 않습니다.\"}";
    static final String UNKNOWN = "{\"outcome\":\"UNAVAILABLE\",\"serviceId\":\"VPN\",\"code\":\"UNKNOWN\",\"receivedCode\":\"RATE_LIMITED\",\"message\":\"상태 조회 서버가 알 수 없는 원인 값을 보냈습니다.\"}";

    static ArrayNode data() throws Exception {
        return (ArrayNode) StatusWire.JSON.readTree(Files.readString(Path.of("data/services.json")));
    }

    static void write(Path directory, Object value) throws Exception {
        Files.writeString(directory.resolve("services.json"), StatusWire.text(value));
    }

    static ServerParameters server(Path directory) {
        return StatusClient.serverParameters(directory, System.getProperty("inquiry.server.classpath"));
    }

    static McpSyncClient connect(ServerParameters parameters) {
        var client = McpClient.sync(new StdioClientTransport(parameters, McpJsonDefaults.getMapper()))
                .requestTimeout(Duration.ofSeconds(10)).initializationTimeout(Duration.ofSeconds(10)).build();
        try { client.initialize(); return client; }
        catch (RuntimeException e) { client.closeGracefully(); throw e; }
    }

    static ServerParameters fixture(String mode) {
        return ServerParameters.builder(java()).args("-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("inquiry.test.classpath"), ProtocolFixture.class.getName(), mode).build();
    }

    static void assertWire(CallToolResult response, String expected, boolean isError) {
        assertEquals(isError, response.isError());
        assertEquals(expected, StatusWire.text(response.structuredContent()));
        assertEquals(1, response.content().size());
        assertEquals(expected, assertInstanceOf(TextContent.class, response.content().get(0)).text());
        var validation = McpJsonDefaults.getSchemaValidator().validate(StatusWire.outputSchema(), response.structuredContent());
        assertTrue(validation.valid(), validation.errorMessage());
    }

    static String java() { return Path.of(System.getProperty("java.home"), "bin", "java").toString(); }

    record Execution(String out, String err, int exit) {}

    static Execution runMain(Class<?> main, String... args) throws Exception {
        var command = new ArrayList<>(List.of(java(), "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("inquiry.test.classpath"), main.getName()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command);
        // 검사 JVM의 소켓 옵션은 새 Java 앱에 필요하지 않다. 출력에도 로컬 경로를 남기지 않는다.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        var process = builder.start();
        process.getOutputStream().close();
        var stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        var stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            assertTrue(process.waitFor(25, TimeUnit.SECONDS), "진입점 실행 제한 시간");
            return new Execution(stdout.get(2, TimeUnit.SECONDS), stderr.get(2, TimeUnit.SECONDS), process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String read(java.io.InputStream input) {
        try (input) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
