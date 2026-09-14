package lab.week08;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static lab.week08.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class HybridSearchTest {
    @TempDir Path temp;
    Chunk chunk(String id, String text, String tenant) {
        return new Chunk(id, id, text, id + ".md", "current", tenant, true, "1", id, "", false);
    }
    @Test void actualBm25UsesKoreanTermsAndKeepsAccessScope() throws Exception {
        var refund = chunk("refund", "이중 결제 환불 접수 기한은 결제일로부터 30일입니다.", "public");
        var shipping = chunk("shipping", "배송 현황은 주문 화면에서 확인합니다.", "tenant-alpha");
        try (var index = new Bm25Index(List.of(refund, shipping), "tenant-alpha")) {
            assertEquals("refund", index.retrieve("환불 기한", 2).get(0).chunk().documentId());
            assertTrue(index.retrieve("zzzznotfound", 2).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> new Bm25Index(
            List.of(chunk("private", "환불", "tenant-beta")), "tenant-alpha"));
    }
    @Test void reciprocalRanksIgnoreIncomparableScoresAndDeduplicate() {
        var a = chunk("a", "환불", "public"); var b = chunk("b", "배송", "public");
        var combined = HybridSearch.fuse(List.of(new RetrievedChunk(a, 999), new RetrievedChunk(a, 998), new RetrievedChunk(b, 2)),
            List.of(new RetrievedChunk(b, .99)), 2);
        assertEquals("b", combined.get(0).chunk().chunkId());
        assertEquals(1.0/62 + 1.0/61, combined.get(0).lexicalScore(), 1e-9);
        assertEquals(1.0/61, combined.get(1).lexicalScore(), 1e-9);
    }
    @Test void sameChunksFeedBm25RealSdkStoreAndFinalEvidence() throws Exception {
        var chunks = List.of(chunk("refund", "환불은 결제일로부터 30일 안에 접수합니다.", "public"),
                             chunk("shipping", "배송 정보를 조회합니다.", "public"));
        EmbeddingIndex.Embedder fake = texts -> texts.stream().map(t -> t.contains("환불")
            ? new float[]{1, 0} : new float[]{0, 1}).toList();
        var vector = EmbeddingIndex.build(chunks, temp.resolve("index.json"), "tenant-alpha", "fixture", fake);
        try (var bm25 = new Bm25Index(chunks, "tenant-alpha")) {
            var hits = HybridSearch.fuse(bm25.retrieve("환불", 2), vector.retrieve("환불", 2, 0), 2);
            assertEquals("refund", hits.get(0).chunk().documentId());
            assertTrue(SearchComparison.evidence("환불", hits).toString().contains("30일"));
        }
    }
}
