package lab.week08;

import java.util.*;
import static lab.week08.Models.*;

public final class Rerank {
    public static final List<String> ARCHIVE_HINTS = List.of("예전", "과거", "보관", "이전 버전");
    public static boolean wantsArchive(String query) { return ARCHIVE_HINTS.stream().anyMatch(query::contains); }
    public static List<RankedChunk> rerank(String query, List<RetrievedChunk> results, int topK) {
        Set<String> terms = new HashSet<>(Retrieval.tokenize(query));
        boolean archive = wantsArchive(query);
        var ranked = new ArrayList<RankedChunk>();
        for (var item : results) {
            var chunk = item.chunk();
            double score = item.lexicalScore();
            var reasons = new ArrayList<>(List.of("lexical"));
            if (chunk.status().equals("current") && !archive) { score += 0.12; reasons.add("current-policy"); }
            if (chunk.status().equals("archived") && archive) { score += 0.18; reasons.add("archive-requested"); }
            if (Retrieval.tokenize(chunk.documentId().replace('-', ' ')).stream().anyMatch(terms::contains)) {
                score += 0.04; reasons.add("identifier-overlap");
            }
            ranked.add(new RankedChunk(chunk, item.lexicalScore(), Math.rint(score * 1e6) / 1e6, List.copyOf(reasons)));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(RankedChunk::rerankScore).reversed()
                .thenComparing(item -> item.chunk().chunkId())).limit(topK).toList();
    }
}
