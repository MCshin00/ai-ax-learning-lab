package lab.week08;

import java.util.*;
import static lab.week08.Models.*;

/** A forced-candidate comparison of evidence scope, not a retrieval quality benchmark. */
public final class ContextChoices {
    public static Map<String, String> selectedOnly(Chunk hit) {
        return Map.of(hit.documentId(), hit.text());
    }
    public static Map<String, String> parentDocument(Chunk hit, List<Chunk> allowedChunks) {
        String body = allowedChunks.stream()
                .filter(c -> c.documentId().equals(hit.documentId()) && c.version().equals(hit.version())
                        && c.tenantId().equals(hit.tenantId()))
                .map(Chunk::text).reduce((a, b) -> a + "\n\n" + b).orElseThrow();
        return Map.of(hit.documentId(), body);
    }
    public static void main(String[] args) throws Exception {
        var documents = Chunking.loadDocuments(Quickstart.KNOWLEDGE_BASE).stream()
                .filter(d -> d.documentId().equals("refund-policy")).toList();
        var chunks = Chunking.splitDocuments(documents, 80, 0).stream()
                .filter(c -> Retrieval.eligible(c, "tenant-alpha", false, false)).toList();
        var hit = chunks.stream().filter(c -> c.text().contains("접수 기간")).findFirst().orElseThrow();
        System.out.println(Quickstart.json(Map.of(
                "mode", "FORCED_CANDIDATE", "query", "소유권 확인 전에도 이중 결제면 바로 환불되나요?",
                "candidate", hit.chunkId(), "selected_only", selectedOnly(hit),
                "parent_document", parentDocument(hit, chunks), "model_called", false)));
    }
}
