package lab.week11;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import static lab.week11.AiPort.Source;

/** 접수 → 주문 조회 → 검색 에이전트 → 검사·한 번의 보완 → 결과. */
public final class Consultation {
    public record Intake(String intent, List<String> orderIds, Map<String, Integer> receivedDays,
                         Map<String, Boolean> used) {}
    public record DraftItem(String orderId, String answer, List<String> sourceIds) {}
    public record Draft(List<DraftItem> items) {}
    public record Action(String orderId, String action) {}

    public static final class Item {
        public String orderId, lookupStatus, status = "pending", answer = "", question = "", nextAction = "review";
        public JsonNode facts;
        public List<Source> sources = List.of();
        Item(OrderLookup.Fact fact) {
            orderId = fact.orderId(); lookupStatus = fact.status(); facts = fact.facts();
        }
    }
    public static final class Result {
        public String conversationId, status = "needs_review", question = "", mode = "";
        public Intake intake;
        public List<Item> items = new ArrayList<>();
        public List<Object> trace = new ArrayList<>();
        public List<String> feedback = List.of();
        public List<Action> actions = List.of();
        public int revisions, modelCalls;
        public boolean modelCallsKnown = true;
        Result(String id) { conversationId = id; }
    }

    private final AiPort ai;
    private final Function<String, OrderLookup.Fact> lookup;
    private final Map<String, Source> policies;
    private final Map<String, Intake> sessions = new HashMap<>();
    private final int maxCalls;
    private final boolean repair;
    private final String initialContext;
    private static final Pattern ORDER_ID = Pattern.compile("(?<![A-Z0-9_-])A-\\d{3}(?![A-Z0-9_-])");

    public Consultation(AiPort ai, Function<String, OrderLookup.Fact> lookup, List<Source> policies) {
        this(ai, lookup, policies, 6, true, "excerpt");
    }
    public Consultation(AiPort ai, Function<String, OrderLookup.Fact> lookup, List<Source> policies,
                        int maxCalls, boolean repair, String initialContext) {
        if (maxCalls < 1 || maxCalls > 6 || !Set.of("excerpt", "full").contains(initialContext))
            throw new IllegalArgumentException("호출 상한과 문맥 범위를 확인하세요.");
        this.ai = ai; this.lookup = lookup; this.maxCalls = maxCalls;
        this.repair = repair; this.initialContext = initialContext;
        this.policies = new LinkedHashMap<>();
        for (var source : policies) this.policies.put(source.sourceId(), source);
    }

    // 예제는 한 요청씩 처리합니다. 실행 중의 대화 정보는 Java 앱이 소유합니다.
    public synchronized Result reply(String conversationId, String request) {
        if (conversationId == null || conversationId.isBlank() || conversationId.length() > 100
                || request == null || request.isBlank() || request.length() > 4000)
            throw new IllegalArgumentException("대화 ID와 문의의 길이를 확인하세요.");
        var result = new Result(conversationId);
        var explicit = ORDER_ID.matcher(request.toUpperCase(Locale.ROOT)).results()
            .map(m -> m.group()).distinct().toList();
        var previous = sessions.get(conversationId);
        if (previous != null && !explicit.isEmpty() && !new HashSet<>(explicit).equals(new HashSet<>(previous.orderIds())))
            previous = new Intake(previous.intent(), previous.orderIds(), Map.of(), Map.of());
        try {
            // 모델이 해석할 문장과 이전 접수만 전달합니다.
            var input = new LinkedHashMap<String, Object>();
            input.put("request", request); input.put("previous", previous);
            var parsed = Json.convert(invoke("intake", input, result).value(), Intake.class);
            var intake = normalize(parsed, explicit, previous);
            result.intake = intake;
            sessions.put(conversationId, intake);
            if (intake.intent().equals("unsupported")) {
                result.question = "제공 자료로 판단할 수 없습니다. 담당자에게 확인하세요.";
                return result;
            }
            if (intake.orderIds().isEmpty()) {
                result.status = "needs_input"; result.question = "주문 번호를 알려 주세요.";
                return result;
            }
            // 주문 조회는 모델의 도구 선택을 기다리지 않고 Java가 실행합니다.
            for (var id : intake.orderIds()) {
                OrderLookup.Fact fact;
                try { fact = lookup.apply(id); }
                catch (RuntimeException e) { fact = new OrderLookup.Fact("unavailable", id, null); }
                result.items.add(new Item(fact));
                result.trace.add(Map.of("stage", "lookup", "order_id", id, "result", fact));
            }
            var evidence = new LinkedHashMap<String, Source>();
            for (int attempt = 0; attempt < (repair ? 2 : 1); attempt++) {
                var payload = Map.of("request", request, "intake", intake, "orders", result.items,
                    "evidence", List.copyOf(evidence.values()), "feedback", result.feedback,
                    "initial_context", initialContext);
                var response = invoke("draft", payload, result);
                for (var source : response.sources()) evidence.put(source.sourceId(), source);
                var draft = Json.convert(response.value(), Draft.class);
                if (draft.items().stream().anyMatch(row -> !intake.orderIds().contains(row.orderId())))
                    throw new IllegalArgumentException("요청하지 않은 주문의 초안입니다.");
                result.feedback = check(draft, intake, result.items, evidence);
                result.trace.add(Map.of("stage", "check", "attempt", attempt, "draft", draft, "feedback", result.feedback));
                finish(draft, intake, result, evidence);
                if (result.feedback.isEmpty() || attempt == 1 || !repair || evidence.isEmpty()) break;
                // 보완 여부를 결정하고 다음 모델 호출에 확장한 근거를 전달합니다.
                evidence.replaceAll((id, source) -> {
                    var full = policies.get(id);
                    return full == null ? source : new Source(id, full.title(), full.text(), source.score(), "full");
                });
                result.revisions = 1;
                result.trace.add(Map.of("stage", "expand", "sources", List.copyOf(evidence.values())));
            }
        } catch (Stopped e) { result.status = e.status; }
        catch (AiPort.Unavailable e) {
            result.status = "unavailable"; result.modelCallsKnown = false;
        } catch (IllegalArgumentException | NullPointerException e) { result.status = "invalid_output"; }
        finally {
            for (var item : result.items) if (item.status.equals("pending")) {
                item.status = "needs_review";
                item.question = "처리를 완료하지 못했습니다. 확인된 사실과 남은 처리를 검토하세요.";
            }
            result.actions = result.items.stream().map(i -> new Action(i.orderId, i.nextAction)).toList();
        }
        return result;
    }

    private AiPort.Reply invoke(String operation, Object payload, Result result) {
        int remaining = maxCalls - result.modelCalls;
        if (remaining <= 0) throw new Stopped("limit_reached");
        var response = ai.invoke(operation, payload, remaining);
        if (response.modelCalls() < 0 || response.modelCalls() > remaining
                || ("ok".equals(response.status()) && response.modelCalls() == 0))
            throw new IllegalArgumentException("호출 횟수 응답이 남은 한도를 벗어났습니다.");
        result.modelCalls += response.modelCalls();
        result.mode = response.mode();
        result.trace.addAll(response.trace());
        if (!response.status().equals("ok")) {
            if (!Set.of("limit_reached", "unavailable", "invalid_output").contains(response.status()))
                throw new IllegalArgumentException("알 수 없는 모델 처리 결과입니다.");
            throw new Stopped(response.status());
        }
        return response;
    }

    private static Intake normalize(Intake input, List<String> explicit, Intake previous) {
        if (!Set.of("status", "cancel", "return", "unsupported").contains(input.intent()))
            throw new IllegalArgumentException("지원하지 않는 문의 종류입니다.");
        var allowed = !explicit.isEmpty() ? explicit : previous == null ? List.<String>of() : previous.orderIds();
        if (explicit.isEmpty() && !allowed.containsAll(input.orderIds()))
            throw new IllegalArgumentException("입력에 없는 주문 번호입니다.");
        var ids = List.copyOf(new LinkedHashSet<>(explicit.isEmpty() ? input.orderIds() : explicit));
        if (ids.size() > 5) throw new IllegalArgumentException("주문은 한 번에 5개까지 확인합니다.");
        var days = new LinkedHashMap<String, Integer>();
        var used = new LinkedHashMap<String, Boolean>();
        if (input.receivedDays() != null) input.receivedDays().forEach((id, value) -> {
            if (ids.contains(id) && value != null && value >= 0) days.put(id, value);
        });
        if (input.used() != null) input.used().forEach((id, value) -> {
            if (ids.contains(id) && value != null) used.put(id, value);
        });
        return new Intake(input.intent(), ids, Map.copyOf(days), Map.copyOf(used));
    }

    private static String requiredSource(Intake intake, Item item) {
        if (intake.intent().equals("status") || !item.lookupStatus.equals("found")) return null;
        return intake.intent().equals("cancel") && !item.facts.path("shipped").asBoolean() ? "POL-CANCEL" : "POL-RETURN";
    }
    private static boolean supported(DraftItem draft, String required, Map<String, Source> evidence) {
        return draft != null && draft.answer() != null && !draft.answer().isBlank() && draft.sourceIds() != null
            && evidence.keySet().containsAll(draft.sourceIds())
            && (required == null || (draft.sourceIds().contains(required) && evidence.containsKey(required)
                                    && "full".equals(evidence.get(required).scope())));
    }
    private List<String> check(Draft draft, Intake intake, List<Item> items, Map<String, Source> evidence) {
        var feedback = new ArrayList<String>();
        var ids = draft.items().stream().map(DraftItem::orderId).sorted().toList();
        if (!ids.equals(intake.orderIds().stream().sorted().toList())) feedback.add("요청한 주문마다 결과 하나를 반환하세요.");
        for (var item : items) {
            var row = draft.items().stream().filter(d -> item.orderId.equals(d.orderId())).findFirst().orElse(null);
            if (!supported(row, requiredSource(intake, item), evidence))
                feedback.add(item.orderId + ": 검색한 정책의 조건과 신청 경로를 포함한 근거가 필요합니다.");
        }
        return feedback;
    }
    private void finish(Draft draft, Intake intake, Result result, Map<String, Source> evidence) {
        for (var item : result.items) {
            item.answer = ""; item.question = ""; item.sources = List.of(); item.nextAction = "review";
            var matches = draft.items().stream().filter(d -> item.orderId.equals(d.orderId())).toList();
            if (!item.lookupStatus.equals("found")) {
                item.status = item.lookupStatus; item.question = "주문 번호 또는 조회 서비스 상태를 확인하세요.";
            } else if (matches.size() != 1 || !supported(matches.get(0), requiredSource(intake, item), evidence)) {
                item.status = "needs_review"; item.question = "주문별 결과와 정책 근거를 확인해야 합니다.";
            } else if ("POL-RETURN".equals(requiredSource(intake, item)) && item.facts.path("status").asText().equals("배송 완료")
                    && (!intake.receivedDays().containsKey(item.orderId) || !intake.used().containsKey(item.orderId))) {
                item.status = "needs_input"; item.nextAction = "ask"; item.question = "수령 후 며칠이 지났고 상품을 사용했나요?";
            } else {
                item.status = "ready"; item.nextAction = "display"; item.answer = matches.get(0).answer();
                item.sources = matches.get(0).sourceIds().stream().distinct().map(evidence::get).toList();
            }
        }
        var statuses = new HashSet<>(result.items.stream().map(i -> i.status).toList());
        result.status = statuses.equals(Set.of("ready")) ? "ready" : statuses.contains("ready") ? "partial"
            : statuses.contains("needs_input") ? "needs_input" : "needs_review";
    }
    private static final class Stopped extends RuntimeException {
        final String status;
        Stopped(String status) { this.status = status; }
    }
}
