package lab.week03;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.util.*;
import java.util.function.Function;

/** Registration, input contract and result adaptation using the official MCP SDK. */
public final class CatalogServer {
    static final Map<String, Object> STRING = Map.of("type", "string");
    static final Map<String, Object> PRODUCT_SCHEMA = schema(Json.object(
        "product_id", STRING, "name", STRING, "price_krw", Map.of("type", "integer"),
        "in_stock", Map.of("type", "boolean")), "product_id", "name", "price_krw", "in_stock");
    private CatalogServer() {}

    static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        return Json.object("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false);
    }
    static CallToolResult result(Object value) {
        return CallToolResult.builder().content(List.of(new TextContent(Json.text(value))))
            .structuredContent(value).isError(false).build();
    }
    static SyncToolSpecification tool(String name, String description, Map<String, Object> input,
            Map<String, Object> output, boolean readOnly, boolean destructive, boolean idempotent,
            Function<Map<String, Object>, Object> handler) {
        var definition = Tool.builder().name(name).description(description)
            .inputSchema(Json.MAPPER, Json.text(input)).outputSchema(output)
            .annotations(new ToolAnnotations(null, readOnly, destructive, idempotent, false, null)).build();
        return SyncToolSpecification.builder().tool(definition).callHandler((exchange, request) -> {
            try { return result(handler.apply(request.arguments() == null ? Map.of() : request.arguments())); }
            catch (IllegalArgumentException | ArithmeticException error) {
                return CallToolResult.builder().content(List.of(new TextContent(error.getMessage())))
                    .isError(true).build();
            }
        }).build();
    }
    static List<SyncToolSpecification> catalogTools(CatalogService catalog) {
        var tools = new ArrayList<SyncToolSpecification>(List.of(tool("get_product", "Look up a fictional product by exact ID: NOTE-01 or PEN-02. No purchase occurs.",
            schema(Map.of("product_id", STRING), "product_id"), PRODUCT_SCHEMA, true, false, true, input -> {
                Json.keys(input, "product_id");
                return catalog.getProduct(Json.string(input, "product_id"));
            })));
        tools.addAll(InspectorServer.extraCatalogTools(catalog));
        return tools;
    }
    public static McpSyncServer create(StdioServerTransportProvider transport) {
        CatalogService catalog = new CatalogService();
        return create(transport, catalog, catalogTools(catalog));
    }
    static McpSyncServer create(StdioServerTransportProvider transport, CatalogService catalog, List<SyncToolSpecification> tools) {
        return McpServer.sync(transport).serverInfo("learning-catalog", "1.0.0")
            .capabilities(ServerCapabilities.builder().tools(false).resources(false, false).prompts(false).build())
            .tools(tools)
            .resources(new SyncResourceSpecification(Resource.builder().uri("catalog://help").name("catalog_help")
                .description("Fictional catalog scope").mimeType("text/plain").build(),
                (exchange, request) -> new ReadResourceResult(List.of(new TextResourceContents("catalog://help", "text/plain", catalog.catalogHelp())))))
            .prompts(new SyncPromptSpecification(new Prompt("explain_product", "Prepare a request to describe one product.",
                List.of(new PromptArgument("product_id", "Exact catalog product ID", true))),
                (exchange, request) -> new GetPromptResult("Catalog explanation request", List.of(new PromptMessage(Role.USER,
                    new TextContent(catalog.explainProduct(Json.string(request.arguments(), "product_id"))))))))
            .build();
    }
    public static void main(String[] args) {
        create(new StdioServerTransportProvider(Json.MAPPER));
    }
}
