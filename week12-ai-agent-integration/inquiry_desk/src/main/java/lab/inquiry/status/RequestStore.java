package lab.inquiry.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static lab.inquiry.status.RequestResult.Outcome.*;

final class RequestStore {
    record Stored(String requestId, RequestData request) {}
    private final Path file;
    private final ServiceCatalog catalog;
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    RequestStore(Path directory, ServiceCatalog catalog) {
        file = directory.resolve("requests.json");
        this.catalog = catalog;
    }

    RequestResult save(Map<String, Object> arguments) {
        if (!RequestWire.valid("save", arguments)) return RequestWire.invalid("save");
        RequestData data = RequestWire.data(arguments);
        List<Stored> rows;
        try { rows = read(); }
        catch (IOException | RuntimeException e) { return unreadable(); }
        for (var row : rows) {
            if (row.request().reviewId().equals(data.reviewId())) {
                return row.request().equals(data) ? RequestResult.saved(row.requestId())
                        : RequestResult.error(CONFLICT, null, "CONTENT_MISMATCH", "같은 검토본으로 다른 내용이 이미 저장되어 있습니다.");
            }
        }
        for (var service : data.services()) {
            var current = catalog.lookup(Map.of("serviceId", service.serviceId()));
            if (current.outcome() == LookupResult.Outcome.UNAVAILABLE) return RequestResult.unavailable(current.code(), current.message());
            if (current.outcome() != LookupResult.Outcome.FOUND || current.revision() != service.revision()) {
                return RequestResult.error(STATUS_CHANGED, service.serviceId(), "REVISION_MISMATCH",
                        "검토한 뒤 서비스 상태가 바뀌었습니다. 다시 문의해 검토해 주세요.");
            }
        }
        String id = String.format(Locale.ROOT, "REQ-%04d", rows.size() + 1);
        rows.add(new Stored(id, data));
        try { Files.writeString(file, json.writeValueAsString(rows)); }
        catch (IOException e) {
            return RequestResult.unavailable("REQUESTS_UNWRITABLE", "요청을 저장할 수 없습니다.");
        }
        return RequestResult.saved(id);
    }

    RequestResult get(Map<String, Object> arguments) {
        if (!RequestWire.valid("get", arguments)) return RequestWire.invalid("get");
        String id = (String) arguments.get("requestId");
        try {
            return read().stream().filter(row -> row.requestId().equals(id)).findFirst()
                    .map(row -> RequestResult.found(id, row.request())).orElseGet(() -> RequestResult.absent(id));
        } catch (IOException | RuntimeException e) { return unreadable(); }
    }

    private List<Stored> read() throws IOException {
        try { return json.readValue(Files.readString(file), new TypeReference<ArrayList<Stored>>() {}); }
        catch (NoSuchFileException e) { return new ArrayList<>(); }
    }

    private static RequestResult unreadable() {
        return RequestResult.unavailable("REQUESTS_UNREADABLE", "저장된 요청을 읽을 수 없습니다.");
    }
}
