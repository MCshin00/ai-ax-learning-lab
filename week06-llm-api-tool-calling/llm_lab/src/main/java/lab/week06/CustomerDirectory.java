package lab.week06;

import java.util.LinkedHashMap;
import java.util.Map;

/** Supplied business data. It has no model, Tool schema, or conversation state. */
public final class CustomerDirectory {
    private CustomerDirectory() {}
    public static final Map<String, Map<String, Object>> CUSTOMERS = new LinkedHashMap<>(
            Map.of("C-100", Map.of("plan", "basic", "status", "active")));

    public static Map<String, Object> lookup(String id) {
        if (id == null || id.isBlank()) return Map.of("error", "INVALID_CUSTOMER_ID");
        var customer = CUSTOMERS.get(id);
        if (customer == null) return Map.of("error", "CUSTOMER_NOT_FOUND");
        var result = new LinkedHashMap<String, Object>(customer);
        result.put("customer_id", id);
        return result;
    }
}
