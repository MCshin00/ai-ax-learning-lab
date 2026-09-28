package lab.week11;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 같은 요구에서 순서·분기·독립 조회·보완을 선택하는 작은 예제. */
public final class FlowPatterns {
    public static Map<String, Object> route(String kind, String id,
            Function<String, OrderLookup.Fact> lookup, Supplier<Object> search) {
        if (kind.equals("status")) return Map.of("order", lookup.apply(id));
        if (kind.equals("cancel")) return Map.of("order", lookup.apply(id), "policy", search.get());
        return Map.of("status", "needs_clarification");
    }
    public static Map<String, OrderLookup.Fact> collectOrders(List<String> ids, Function<String, OrderLookup.Fact> lookup) {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = new LinkedHashMap<String, Future<OrderLookup.Fact>>();
            for (var id : new LinkedHashSet<>(ids)) tasks.put(id, pool.submit(() -> {
                try { return lookup.apply(id); }
                catch (RuntimeException e) { return new OrderLookup.Fact("unavailable", id, null); }
            }));
            var results = new LinkedHashMap<String, OrderLookup.Fact>();
            for (var entry : tasks.entrySet()) results.put(entry.getKey(), entry.getValue().get());
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("조회 대기가 중단됐습니다.", e);
        } catch (ExecutionException e) { throw new IllegalStateException("조회 실행을 확인하세요.", e); }
        finally { pool.shutdownNow(); }
    }
    public record Revision<T>(String status, T draft, List<String> feedback, int revisions) {}
    public static <T> Revision<T> reviseOnce(T draft, Function<T, List<String>> inspect,
                                            BiFunction<T, List<String>, T> revise) {
        var feedback = inspect.apply(draft);
        if (feedback.isEmpty()) return new Revision<>("accepted", draft, feedback, 0);
        var corrected = revise.apply(draft, feedback);
        var remaining = inspect.apply(corrected);
        return new Revision<>(remaining.isEmpty() ? "accepted" : "needs_review", corrected, remaining, 1);
    }
    public static void main(String[] args) throws Exception {
        var lookup = new OrderLookup(Path.of("data/orders.json"));
        System.out.println(Json.write(route("status", "A-102", lookup, () -> Map.of("matches", List.of()))));
        System.out.println(Json.write(collectOrders(List.of("A-102", "A-999", "A-103"), lookup)));
        System.out.println(Json.write(reviseOnce("출처 없는 초안", value -> List.of("출처가 없음"), (value, feedback) -> value)));
    }
}
