package lab.week08;

import java.util.*;
import static lab.week08.Models.*;

/** RRF는 서로 다른 검색 점수가 아닌 각 목록의 순위를 결합합니다. */
public final class HybridSearch {
    public static List<RetrievedChunk> fuse(List<RetrievedChunk> lexical, List<RetrievedChunk> semantic, int topK) {
        if (topK < 1) throw new IllegalArgumentException("후보 수는 양수여야 합니다.");
        var scores = new HashMap<String, Double>();
        var chunks = new HashMap<String, Chunk>();
        for (var ranking : List.of(lexical, semantic)) {
            var seen = new HashSet<String>();
            int rank = 0;
            for (var hit : ranking) {
                var id = hit.chunk().chunkId();
                if (!seen.add(id)) continue;
                var old = chunks.putIfAbsent(id, hit.chunk());
                if (old != null && !old.equals(hit.chunk())) throw new IllegalArgumentException("같은 조각 ID의 원문이 다릅니다.");
                scores.merge(id, 1.0 / (60 + ++rank), Double::sum);
            }
        }
        return scores.keySet().stream().sorted(Comparator.<String>comparingDouble(scores::get).reversed()
            .thenComparing(Comparator.naturalOrder())).limit(topK)
            .map(id -> new RetrievedChunk(chunks.get(id), scores.get(id))).toList();
    }
}
