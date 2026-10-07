package lab.inquiry.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** 매 조회마다 자료를 읽어 변경된 상태와 revision을 함께 반환한다. */
final class ServiceStatusRepository {
    private final Path servicesFile;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    ServiceStatusRepository(Path dataDirectory) {
        servicesFile = dataDirectory.resolve("services.json");
    }

    StatusResult get(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) return StatusResult.invalid(serviceId);
        String source;
        try {
            source = Files.readString(servicesFile);
        } catch (IOException | SecurityException error) {
            return StatusResult.unavailable(serviceId, StatusResult.ErrorCode.DATA_UNREADABLE,
                    "서비스 자료를 읽을 수 없습니다.");
        }
        try {
            JsonNode rows = json.readTree(source);
            if (rows == null || !rows.isArray()) throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            StatusResult found = null;
            for (JsonNode row : rows) {
                if (!row.isObject() || !row.path("id").isTextual() || row.path("id").asText().isBlank()
                        || !ids.add(row.path("id").asText())
                        || !row.path("state").isTextual() || row.path("state").asText().isBlank()
                        || !row.path("detail").isTextual()
                        || !row.path("revision").isIntegralNumber() || !row.path("revision").canConvertToLong()
                        || row.path("revision").longValue() < 0) throw new IllegalArgumentException();
                if (serviceId.equals(row.path("id").asText())) {
                    found = StatusResult.found(serviceId, row.path("state").asText(),
                            row.path("detail").asText(), row.path("revision").longValue());
                }
            }
            return found == null ? StatusResult.notFound(serviceId) : found;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            return StatusResult.unavailable(serviceId, StatusResult.ErrorCode.DATA_INVALID,
                    "서비스 자료의 형식이 올바르지 않습니다.");
        }
    }
}
