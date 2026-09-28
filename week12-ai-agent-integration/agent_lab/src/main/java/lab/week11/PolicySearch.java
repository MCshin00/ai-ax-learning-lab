package lab.week11;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.List;
import static lab.week11.AiPort.Source;

/** 8주차의 임베딩 검색을 상담에서 호출할 수 있는 기능으로 연결합니다. */
public final class PolicySearch {
    public record Result(String status, List<Source> matches) {}
    private final EmbeddingModel embeddings;
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    private final double minScore;
    public PolicySearch(EmbeddingModel embeddings, List<Source> policies) {
        this(embeddings, policies, 0.7);
    }
    public PolicySearch(EmbeddingModel embeddings, List<Source> policies, double minScore) {
        this.embeddings = embeddings; this.minScore = minScore;
        var segments = policies.stream().map(p -> TextSegment.from(p.text(),
            Metadata.from(java.util.Map.of("source_id", p.sourceId(), "title", p.title())))).toList();
        if (!segments.isEmpty()) store.addAll(embeddings.embedAll(segments).content(), segments);
    }
    public Result search(String query) {
        if (query == null || query.isBlank()) return new Result("invalid_input", List.of());
        var request = EmbeddingSearchRequest.builder().queryEmbedding(embeddings.embed(query).content())
            .maxResults(2).minScore(minScore).build();
        var hits = store.search(request).matches().stream().map(hit -> {
            var segment = hit.embedded();
            // 짧은 발췌에서 빠진 조건을 찾아 원문으로 보완하는 실습용 문맥 선택입니다.
            String excerpt = segment.text().split("\\. ", 2)[0] + ".";
            return new Source(segment.metadata().getString("source_id"), segment.metadata().getString("title"),
                excerpt, hit.score(), "excerpt");
        }).toList();
        return new Result(hits.isEmpty() ? "no_evidence" : "found", hits);
    }
}
