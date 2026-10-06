package lab.week08;

import org.junit.jupiter.api.Test;
import java.util.*;
import static lab.week08.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkingComparisonTest {
    List<Document> handbook() throws Exception { return ChunkingComparison.load(ChunkingComparison.EXAMPLE); }
    Document document(String text) {
        return new Document("doc", text, "chunking_example/doc.md", "current", "public", true, "1", null, null, false);
    }
    String body(Chunk chunk) { return chunk.text().substring(chunk.text().indexOf("\n\n") + 2); }
    String context(List<Chunk> chunks, ChunkingComparison.Case item, int topK) throws Exception {
        try (var bm25 = new Bm25Index(chunks, ChunkingComparison.TENANT)) {
            return (String) ChunkingComparison.result(item, bm25::retrieve, topK).get("context");
        }
    }

    @Test void bothSectionSplitsShareChunksAndDifferOnlyInTheFirstLine() throws Exception {
        var plain = ChunkingComparison.sections(handbook());
        var withPath = ChunkingComparison.sectionsWithPath(handbook());
        assertEquals(plain.stream().map(Chunk::chunkId).toList(), withPath.stream().map(Chunk::chunkId).toList());
        assertEquals(plain.stream().map(this::body).toList(), withPath.stream().map(this::body).toList());
        // 경로가 없으면 예외 절 네 개가 어느 유형인지 조각에서 알 수 없습니다.
        assertEquals(4, plain.stream().filter(c -> c.text().startsWith("### 예외\n\n")).count());
        var exception = withPath.stream().filter(c -> c.text().contains("추가 저장 용량")).findFirst().orElseThrow();
        assertTrue(exception.text().startsWith("절 경로: 결제·환불 운영 안내서 > 구독 해지 환불 > 예외\n\n"));
        assertTrue(plain.stream().allMatch(c -> c.source().equals("chunking_example/refund-operations-handbook.md")));
    }

    @Test void pathFollowsHeadingLevelsEvenWhenALevelIsSkipped() {
        var chunks = ChunkingComparison.sectionsWithPath(List.of(document(
            "# 문서\n\n### 가\n\n가 본문\n\n### 나\n\n나 본문\n\n## 다\n\n다 본문\n\n#### 라\n\n라 본문\n\n## 마\n\n마 본문")));
        assertEquals(List.of("절 경로: 문서 > 가", "절 경로: 문서 > 나", "절 경로: 문서 > 다", "절 경로: 문서 > 다 > 라", "절 경로: 문서 > 마"),
            chunks.stream().map(c -> c.text().lines().findFirst().orElseThrow()).toList());
    }

    @Test void paragraphGroupingMixesOtherRequestTypesIntoTheContext() throws Exception {
        var item = ChunkingComparison.CASES.get(0);
        String grouped = context(ChunkingComparison.paragraphs(handbook()), item, 3);
        String bySection = context(ChunkingComparison.sections(handbook()), item, 3);
        // 구독 해지 질문인데 문단 묶기에서는 이중 결제의 예외가 유형 제목 없이 함께 전달됩니다.
        assertTrue(grouped.contains("추가 저장 용량") && grouped.contains("이의 제기가 이미 접수된 경우"));
        assertTrue(bySection.contains("추가 저장 용량") && !bySection.contains("이의 제기가 이미 접수된 경우"));
        assertTrue(grouped.length() > 4 * bySection.length());
    }

    @Test void pathLabelsAnotherTypesDeadlineAndFindsTheRightOneWithMoreCandidates() throws Exception {
        var item = ChunkingComparison.CASES.get(1);
        var plain = ChunkingComparison.sections(handbook());
        var withPath = ChunkingComparison.sectionsWithPath(handbook());
        // 이중 결제 질문에 구독 해지의 접수 기한이 후보로 옵니다. 경로가 없으면 어느 유형의 기한인지 조각에 없습니다.
        assertTrue(context(plain, item, 3).contains("해지일로부터 14일") && !context(plain, item, 3).contains("구독 해지"));
        assertTrue(context(withPath, item, 3).contains("구독 해지 환불 > 접수 기한\n\n접수 기간은 해지일로부터 14일"));
        assertFalse(context(plain, item, 4).contains("결제일로부터 30일"));
        assertTrue(context(withPath, item, 4).contains("이중 결제 환불 > 접수 기한\n\n접수 기간은 결제일로부터 30일"));
    }

    @Test void hybridBringsTogetherTheTwoSectionsEachSearchFoundAlone() throws Exception {
        var item = ChunkingComparison.CASES.get(1);
        var withPath = ChunkingComparison.sectionsWithPath(handbook());
        // 의미 검색 대역입니다. 이름이 같은 접수 기한 절을 먼저 돌려주고 이중 결제의 예외는 네 번째에 둡니다.
        var order = List.of("이중 결제 환불 > 접수 기한", "미제공 서비스 환불 > 접수 기한", "구독 해지 환불 > 접수 기한", "이중 결제 환불 > 예외");
        var ranked = new ArrayList<RetrievedChunk>();
        for (String path : order)
            ranked.add(new RetrievedChunk(withPath.stream().filter(c -> c.text().startsWith("절 경로: 결제·환불 운영 안내서 > " + path + "\n")).findFirst().orElseThrow(), 1.0 - ranked.size() * 0.01));
        ChunkingComparison.Search semantic = (query, k) -> ranked.subList(0, Math.min(k, ranked.size()));
        try (var bm25 = new Bm25Index(withPath, ChunkingComparison.TENANT)) {
            String lexicalOnly = (String) ChunkingComparison.result(item, bm25::retrieve, 3).get("context");
            String semanticOnly = (String) ChunkingComparison.result(item, semantic, 3).get("context");
            String fused = (String) ChunkingComparison.result(item, ChunkingComparison.hybrid(bm25::retrieve, semantic), 3).get("context");
            // 후보 3개에서 BM25는 기한을, 의미 검색 대역은 예외를 놓칩니다. 순위를 합치면 두 절이 함께 남습니다.
            assertTrue(!lexicalOnly.contains("결제일로부터 30일") && lexicalOnly.contains("이의 제기가 이미 접수된 경우"));
            assertTrue(semanticOnly.contains("결제일로부터 30일") && !semanticOnly.contains("이의 제기가 이미 접수된 경우"));
            assertTrue(fused.contains("결제일로부터 30일") && fused.contains("이의 제기가 이미 접수된 경우"));
            // 한쪽 검색에서만 1위였던 서비스 장애의 보상 방식은 결합 후보에서 빠집니다.
            assertTrue(lexicalOnly.contains("서비스 장애 보상 > 보상 방식") && !fused.contains("서비스 장애 보상 > 보상 방식"));
        }
    }

    @Test void candidateCountIsValidatedWhenOptionsAreRead() {
        assertThrows(IllegalArgumentException.class, () -> ChunkingComparison.topK(new String[]{"--top", "0"}));
        assertEquals(4, ChunkingComparison.topK(new String[]{"--top", "4"}));
    }
}
