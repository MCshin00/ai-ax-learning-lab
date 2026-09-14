package lab.week06;

import com.openai.models.responses.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/** 기존 상품 MCP 서버의 공개 계약을 모델 도구로 연결합니다. 상품 데이터는 서버에만 있습니다. */
public final class McpAssistant {
    /** 자식 프로세스의 실행 방식에 의존하지 않도록 lib/*를 실제 JAR 목록으로 풉니다. */
    static String resolvedClasspath(String classpath) {
        var entries = new ArrayList<String>();
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            if (entry.endsWith("*")) {
                var directory = Path.of(entry.substring(0, entry.length() - 1)).toAbsolutePath().normalize();
                try (var files = Files.list(directory)) {
                    var jars = files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().map(Path::toString).toList();
                    if (jars.isEmpty()) throw new IllegalArgumentException("서버 lib 폴더에 JAR가 없습니다. installDist 결과를 확인하세요.");
                    entries.addAll(jars);
                } catch (IOException error) {
                    throw new IllegalArgumentException("서버 lib 폴더를 찾지 못했습니다. 작업 폴더와 경로를 확인하세요.", error);
                }
            } else {
                entries.add(Path.of(entry).toAbsolutePath().normalize().toString());
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    public static final class Catalog implements AutoCloseable {
        private final McpSyncClient client;
        public Catalog(String serverClasspath) {
            this(serverClasspath, "lab.week03.CatalogServer");
        }
        public Catalog(String serverClasspath, String serverMain) {
            var executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            client = McpClient.sync(new StdioClientTransport(ServerParameters.builder(executable)
                .args("-Dfile.encoding=UTF-8", "-cp", resolvedClasspath(serverClasspath), serverMain).build(),
                McpJsonDefaults.getMapper())).requestTimeout(Duration.ofSeconds(20)).build();
            try { client.initialize(); } catch (RuntimeException e) { client.closeGracefully(); throw e; }
        }
        public List<FunctionTool> tools() {
            return client.listTools().tools().stream().filter(t -> t.name().equals("get_product"))
                .map(t -> FunctionTool.builder().name(t.name()).description(t.description()).strict(false)
                    .parameters(Quickstart.JSON.convertValue(t.inputSchema(), FunctionTool.Parameters.class)).build()).toList();
        }
        public Map<String, Object> execute(String name, String arguments) {
            if (!name.equals("get_product")) return Map.of("error", "UNKNOWN_TOOL");
            try {
                var data = Quickstart.ARGUMENTS.readTree(arguments);
                if (data == null || !data.isObject() || data.size() != 1 || !data.path("product_id").isTextual())
                    return Map.of("error", "INVALID_ARGUMENTS");
                var result = client.callTool(new CallToolRequest(name, Map.of("product_id", data.get("product_id").asText())));
                String text = result.content().stream().filter(TextContent.class::isInstance).map(TextContent.class::cast)
                    .map(TextContent::text).collect(java.util.stream.Collectors.joining("\n"));
                return Map.of("status", Boolean.TRUE.equals(result.isError()) ? "tool_error" : "success",
                    "value", result.structuredContent() == null ? text : result.structuredContent());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) { return Map.of("error", "INVALID_ARGUMENTS"); }
              catch (RuntimeException e) { return Map.of("error", "MCP_UNAVAILABLE"); }
        }
        public void close() { client.closeGracefully(); }
    }
    public static Map<String, Object> run(String query, Catalog catalog, Quickstart.Gateway model, String modelName) {
        var tools = catalog.tools();
        if (tools.isEmpty()) throw new IllegalStateException("상품 조회 도구를 찾지 못했습니다.");
        return Quickstart.runWithTools(query, model, modelName, tools,
            "상품은 get_product로 조회하세요. ID가 없으면 물으세요. 상품 조회 실패와 연결 실패를 구별하고 구매를 실행했다고 답하지 마세요.",
            catalog::execute, 3);
    }
    public static void main(String[] args) {
        String classpath = Quickstart.option(args, "--server-classpath", "");
        String library = Quickstart.option(args, "--server-lib", "");
        // Windows Java 실행기가 main 호출 전에 * 인수를 펼칠 수 있어 폴더를 직접 받습니다.
        if (!library.isBlank()) classpath = library + File.separator + "*";
        if (classpath.isBlank()) throw new IllegalArgumentException("3주차 installDist의 lib 폴더를 --server-lib로 지정하세요.");
        boolean live = Arrays.asList(args).contains("--live");
        Quickstart.Gateway model = live ? Quickstart.liveClient() : new Quickstart.Gateway() {
            int count;
            public Quickstart.Turn create(ResponseCreateParams request) {
                if (++count == 1) return new Quickstart.Turn("completed", "", List.of(ResponseOutputItem.ofFunctionCall(
                    ResponseFunctionToolCall.builder().name("get_product").callId("catalog-1")
                        .arguments("{\"product_id\":\"NOTE-01\"}").build())), false);
                var input = request.input().orElseThrow().asResponse();
                return new Quickstart.Turn("completed", "고정 대역이 받은 실제 MCP 결과: "
                    + input.get(input.size()-1).asFunctionCallOutput().output().asString(), List.of(), false);
            }
        };
        try (var catalog = new Catalog(classpath, Quickstart.option(args, "--server-main", "lab.week03.CatalogServer"))) {
            System.out.println(Quickstart.json(Map.of("mode", live ? "LIVE" : "SCRIPTED_MODEL_REAL_MCP",
                "result", run(Quickstart.option(args, "--text", "NOTE-01의 가격과 재고를 알려 주세요."), catalog,
                    model, live ? System.getenv("OPENAI_MODEL") : "offline"))));
        }
    }
}
