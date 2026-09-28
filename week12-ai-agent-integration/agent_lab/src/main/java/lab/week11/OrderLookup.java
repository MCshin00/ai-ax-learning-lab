package lab.week11;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;

/** 주문 자료에서 현재 사실을 조회합니다. */
public final class OrderLookup implements Function<String, OrderLookup.Fact> {
    public record Fact(String status, String orderId, JsonNode facts) {}
    private final Map<String, JsonNode> orders = new LinkedHashMap<>();
    public OrderLookup(Path data) throws IOException {
        for (var row : Json.read(Files.readString(data))) orders.put(row.path("order_id").asText(), row);
    }
    @Override public Fact apply(String input) {
        String id = input.strip().toUpperCase(Locale.ROOT);
        if (!id.matches("A-\\d{3}")) return new Fact("invalid_input", id, null);
        var facts = orders.get(id);
        return new Fact(facts == null ? "not_found" : "found", id, facts == null ? null : facts.deepCopy());
    }
}
