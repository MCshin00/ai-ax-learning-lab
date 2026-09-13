package lab.week08;

import java.util.*;
import static lab.week08.Models.*;

public final class Answering {
    public static final Map<String, String> ABSTENTION_MESSAGES = Map.of(
            "NO_EVIDENCE", "확인할 수 있는 근거 문서가 없어 답변을 보류합니다.",
            "CONFLICTING_SOURCES", "현재 적용 상태로 표시된 문서가 서로 충돌해 담당자 확인이 필요합니다.");
    /** Prepares source excerpts, not a generated answer to the question. */
    public static Map<String, Object> answer(String query, List<RankedChunk> ranked) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("citations", List.of()); payload.put("claims", List.of());
        payload.put("supporting_quotes", Map.of()); payload.put("source_texts", Map.of()); payload.put("failure_reason", null);
        if (ranked.isEmpty()) return abstain(payload, "NO_EVIDENCE");
        var byId = new LinkedHashMap<String, RankedChunk>();
        for (var item : ranked) byId.put(item.chunk().documentId(), item);
        for (var item : ranked) if (item.chunk().conflictsWith() != null && byId.containsKey(item.chunk().conflictsWith())) {
            var pair = List.of(item, byId.get(item.chunk().conflictsWith()));
            payload.put("citations", citations(pair)); payload.put("source_texts", sourceTexts(pair));
            return abstain(payload, "CONFLICTING_SOURCES");
        }
        var selected = new ArrayList<RankedChunk>();
        var seen = new HashSet<String>();
        for (var item : ranked) {
            if (seen.contains(item.chunk().documentId()) || seen.size() < 2) {
                seen.add(item.chunk().documentId()); selected.add(item);
            }
        }
        var quotes = new LinkedHashMap<String, String>();
        var claims = new ArrayList<Map<String, String>>();
        var text = new StringBuilder("근거 문서에서 확인한 내용:");
        for (var item : selected) {
            String id = item.chunk().documentId(), quote = supportingSentence(item.chunk().text());
            quotes.put(id, quote); claims.add(Map.of("text", quote, "document_id", id));
            text.append("\n- ").append(quote).append(" [").append(id).append(']');
        }
        payload.put("status", "ANSWERED"); payload.put("answer", text.toString()); payload.put("claims", claims);
        payload.put("citations", citations(selected)); payload.put("supporting_quotes", quotes); payload.put("source_texts", sourceTexts(selected));
        return payload;
    }
    static Map<String, Object> abstain(Map<String, Object> payload, String reason) {
        payload.put("status", "ABSTAINED"); payload.put("answer", ABSTENTION_MESSAGES.get(reason)); payload.put("failure_reason", reason);
        return payload;
    }
    static List<Map<String, String>> citations(List<RankedChunk> selected) {
        return selected.stream().map(item -> Map.of("document_id", item.chunk().documentId(), "chunk_id", item.chunk().chunkId())).toList();
    }
    static Map<String, String> sourceTexts(List<RankedChunk> selected) {
        var sources = new LinkedHashMap<String, String>();
        selected.forEach(item -> sources.merge(item.chunk().documentId(), item.chunk().text(), (a, b) -> a + "\n\n" + b));
        return sources;
    }
    static String supportingSentence(String text) {
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String value = paragraph.strip().replaceFirst("^[# ]+", "");
            if (value.length() >= 20) return value.lines().findFirst().orElse("");
        }
        String value = text.strip();
        return value.substring(0, Math.min(240, value.length()));
    }
}
