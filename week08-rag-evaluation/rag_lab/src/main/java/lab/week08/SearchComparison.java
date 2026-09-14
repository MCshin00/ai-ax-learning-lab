package lab.week08;

import java.nio.file.Path;
import java.util.*;
import static lab.week08.Models.*;

/** 같은 조각·접근 범위에서 검색 결과를 비교합니다. 기본 실행은 BM25만 사용합니다. */
public final class SearchComparison {
    static Map<String, Object> evidence(String query, List<RetrievedChunk> hits) {
        return evidence(query, hits, null);
    }
    static Map<String, Object> evidence(String query, List<RetrievedChunk> hits, Quickstart.Generator generator) {
        var context = hits.stream().limit(4).map(h -> new RankedChunk(h.chunk(), h.lexicalScore(),
            h.lexicalScore(), List.of("검색 순위 유지"))).toList();
        var evidence = Answering.answer(query, context);
        evidence.put("retrieved_document_ids", hits.stream().map(h -> h.chunk().documentId()).toList());
        return Map.of("candidates", hits.stream().map(h -> Map.of("chunk_id", h.chunk().chunkId(),
            "document_id", h.chunk().documentId(), "score", h.lexicalScore())).toList(),
            "evidence", evidence, "answer_result", Quickstart.finish(query, evidence, generator));
    }
    public static void main(String[] args) throws Exception {
        boolean live = Arrays.asList(args).contains("--live");
        boolean answer = Arrays.asList(args).contains("--answer");
        if (answer && !live) throw new IllegalArgumentException("답변 생성은 --live --answer로 실행하세요.");
        Quickstart.Generator generator = answer ? Quickstart::generateLive : null;
        String query = Quickstart.option(args, "--query", "이중 결제 환불 접수 기한은 며칠인가요?");
        Path kb = Path.of(Quickstart.option(args, "--kb", "../knowledge_base"));
        var chunks = EmbeddingIndex.currentChunks(kb, "tenant-alpha");
        var results = new LinkedHashMap<String, Object>();
        try (var bm25 = new Bm25Index(chunks, "tenant-alpha")) {
            var lexical = bm25.retrieve(query, 8);
            results.put("bm25", evidence(query, lexical, generator));
            if (live) {
                String model = System.getenv("OPENAI_EMBEDDING_MODEL");
                var index = EmbeddingIndex.open(chunks, Path.of(Quickstart.option(args, "--index", ".local/semantic-index.json")),
                    "tenant-alpha", model, EmbeddingIndex.live(model));
                var semantic = index.retrieve(query, 8, 0.0);
                results.put("embedding", evidence(query, semantic, generator));
                results.put("hybrid", evidence(query, HybridSearch.fuse(lexical, semantic, 8), generator));
            }
        }
        System.out.println(Quickstart.json(Map.of("query", query, "mode", live ? "REAL_EMBEDDING" : "BM25_ONLY",
            "answer_generation", answer ? "REQUESTED" : "NOT_RUN", "results", results)));
    }
}
