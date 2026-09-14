package lab.week08;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingIndexTest {
    @TempDir Path temp;
    // Fixed vectors validate SDK persistence/wiring, not semantic retrieval quality.
    final EmbeddingIndex.Embedder fake = texts -> texts.stream()
            .map(t -> t.contains("환불") ? new float[]{1, 0} : new float[]{0, 1}).toList();
    @Test void applicationChosenSegmentationSurvivesAndCannotReuseADifferentSnapshot() throws Exception {
        var documents = Chunking.loadDocuments(Quickstart.KNOWLEDGE_BASE);
        var selected = Chunking.splitDocuments(documents, 80, 0).stream()
                .filter(c -> Retrieval.eligible(c, "tenant-alpha", false, false)).toList();
        Path file = temp.resolve("selected.json");
        EmbeddingIndex.build(selected, file, "tenant-alpha", "fixture", fake);
        var index = EmbeddingIndex.open(selected, file, "tenant-alpha", "fixture", fake);
        assertTrue(index.retrieve("환불", 100, 0).stream().allMatch(r -> selected.contains(r.chunk())));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingIndex.open(
                selected.subList(1, selected.size()), file, "tenant-alpha", "fixture", fake));
        var invalid = new ArrayList<>(selected); invalid.add(selected.get(0));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingIndex.build(invalid, file, "tenant-alpha", "fixture", fake));
    }
    @Test void sdkStoreSurvivesReopenAndDoesNotEmbedDocumentsAgain() throws Exception {
        Path file = temp.resolve("index.json");
        EmbeddingIndex.build(Quickstart.KNOWLEDGE_BASE, file, "tenant-alpha", "fixture", fake);
        var calls = new ArrayList<List<String>>();
        var index = EmbeddingIndex.open(Quickstart.KNOWLEDGE_BASE, file, "tenant-alpha", "fixture", texts -> {
            calls.add(texts); return fake.embed(texts);
        });
        var found = index.retrieve("환불", 8, .6);
        assertTrue(found.stream().anyMatch(c -> c.chunk().documentId().equals("refund-policy")));
        assertFalse(found.stream().anyMatch(c -> c.chunk().tenantId().equals("tenant-beta") || !c.chunk().trusted()));
        assertEquals(List.of(List.of("환불")), calls);
        assertThrows(IllegalArgumentException.class, () -> EmbeddingIndex.open(Quickstart.KNOWLEDGE_BASE, file, "tenant-beta", "fixture", fake));
    }
    @Test void changedOrRemovedDocumentsRequireRebuildAndRebuildReplacesOldChunks() throws Exception {
        Path kb = temp.resolve("kb"); Files.createDirectories(kb);
        try (var paths = Files.list(Quickstart.KNOWLEDGE_BASE)) {
            for (var p : paths.filter(Files::isRegularFile).toList()) Files.copy(p, kb.resolve(p.getFileName()));
        }
        // Keep the update fixture independent of the learner's current policy revision.
        Files.writeString(kb.resolve("refund-policy.md"), "# 환불 정책\n\n환불 접수 기한은 결제일로부터 30일이다.");
        Path file = temp.resolve("index.json");
        EmbeddingIndex.build(kb, file, "tenant-alpha", "fixture", fake);
        Files.writeString(kb.resolve("refund-policy.md"), Files.readString(kb.resolve("refund-policy.md")).replace("30일", "45일"));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingIndex.open(kb, file, "tenant-alpha", "fixture", fake));
        EmbeddingIndex.build(kb, file, "tenant-alpha", "fixture", fake);
        var index = EmbeddingIndex.open(kb, file, "tenant-alpha", "fixture", fake);
        assertTrue(index.retrieve("환불", 8, .6).stream().filter(c -> c.chunk().documentId().equals("refund-policy"))
                .allMatch(c -> c.chunk().text().contains("45일") && !c.chunk().text().contains("30일")));
        Files.delete(kb.resolve("refund-policy.md"));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingIndex.open(kb, file, "tenant-alpha", "fixture", fake));
        EmbeddingIndex.build(kb, file, "tenant-alpha", "fixture", fake);
        assertTrue(EmbeddingIndex.open(kb, file, "tenant-alpha", "fixture", fake).retrieve("환불", 8, .6).stream()
                .noneMatch(c -> c.chunk().documentId().equals("refund-policy")));
    }
}
