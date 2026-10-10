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

        for (JsonNode row : rows) {
            if (id.equals(row.path("id").textValue())) {
                if (!row.path("state").isTextual() || !row.path("detail").isTextual()
                        || !row.path("revision").isIntegralNumber()) return LookupResult.failed(id, DATA_INVALID);
                return LookupResult.found(id, row.path("state").textValue(), row.path("detail").textValue(),
                        row.path("revision").longValue());
            }
        }
        return LookupResult.absent(id);
    }
}
