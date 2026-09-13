package lab.week08;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class Pipeline {
    public static Map<String, Object> run(String query, Path knowledgeBase, String tenantId, double minScore, boolean includeFixtures) throws IOException {
        var documents = Chunking.loadDocuments(knowledgeBase);
        var chunks = Chunking.splitDocuments(documents);
        boolean archive = Rerank.wantsArchive(query);
        var retrieved = Retrieval.retrieve(query, chunks, tenantId, 8, minScore, archive, includeFixtures);
        var ranked = Rerank.rerank(query, retrieved, 4);
        var result = Answering.answer(query, ranked);
        result.put("answer_mode", "evidence_excerpts"); result.put("semantic_review_status", "NOT_VERIFIED");
        result.put("retrieved_document_ids", retrieved.stream().map(item -> item.chunk().documentId()).toList());
        result.put("ranked_document_ids", ranked.stream().map(item -> item.chunk().documentId()).toList());
        result.put("stage_trace", Map.of("document_count", documents.size(), "chunk_count", chunks.size(),
                "retrieved_count", retrieved.size(), "ranked_count", ranked.size(), "include_archived", archive,
                "include_fixtures", includeFixtures, "tenant_id", tenantId));
        return result;
    }
}
