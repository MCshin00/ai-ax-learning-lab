package lab.inquiry.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static lab.inquiry.status.LookupResult.Cause.*;

/** 인수 → 파일 전체 → 찾는 ID의 행 순으로 판단한다. 조회 사이에는 자료를 보관하지 않는다. */
final class ServiceCatalog {
    private final Path file;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    ServiceCatalog(Path directory) { file = directory.resolve("services.json"); }

    LookupResult lookup(Map<String, Object> arguments) {
        if (arguments == null || arguments.size() != 1
                || !(arguments.get("serviceId") instanceof String id) || id.isBlank()) {
            return LookupResult.invalid();
        }
        String source;
        try { source = Files.readString(file); }
        catch (IOException | SecurityException e) { return LookupResult.failed(id, DATA_UNREADABLE); }

        JsonNode rows;
        try { rows = json.readTree(source); }
        catch (JsonProcessingException e) { return LookupResult.failed(id, DATA_INVALID); }
        if (rows == null || !rows.isArray()) return LookupResult.failed(id, DATA_INVALID);

        boolean unreadableId = false;
        JsonNode match = null;
        int matches = 0;
        for (JsonNode row : rows) {
            if (!row.isObject() || !nonblank(row.path("id"))) {
                unreadableId = true;
            } else if (id.equals(row.path("id").textValue())) {
                matches++;
                match = row;
            }
        }
        if (matches == 0) return unreadableId ? LookupResult.failed(id, DATA_INVALID) : LookupResult.absent(id);
        if (matches != 1 || !validRow(match)) return LookupResult.failed(id, DATA_INVALID);
        return LookupResult.found(id, match.path("state").textValue(), match.path("detail").textValue(),
                match.path("revision").longValue());
    }

    private static boolean nonblank(JsonNode node) { return node.isTextual() && !node.textValue().isBlank(); }

    private static boolean validRow(JsonNode row) {
        var state = row.path("state");
        var revision = row.path("revision");
        return state.isTextual() && (state.textValue().equals("normal") || state.textValue().equals("incident"))
                && nonblank(row.path("detail")) && revision.isIntegralNumber() && revision.longValue() >= 0;
    }
}
