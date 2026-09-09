package lab.week03;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** JSON conversion at the application boundary; MCP transport belongs to the SDK. */
final class Json {
    static final McpJsonMapper MAPPER = McpJsonDefaults.getMapper();
    private Json() {}
    static Map<String, Object> object(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    static String text(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
    static Map<String, Object> parse(byte[] value) {
        try { return MAPPER.readValue(value, new TypeRef<Map<String, Object>>() {}); }
        catch (IOException error) { throw new IllegalArgumentException("INVALID_JSON: expected an object.", error); }
    }
    static String string(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof String value)) throw new IllegalArgumentException("INVALID_INPUT: " + key + " must be a string.");
        return value;
    }
    static void keys(Map<String, Object> input, String... allowed) {
        if (!Set.of(allowed).containsAll(input.keySet())) throw new IllegalArgumentException("INVALID_INPUT: unexpected field.");
    }
}
