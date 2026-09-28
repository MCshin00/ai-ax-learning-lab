package lab.week08;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static lab.week08.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchComparisonTest {
    @TempDir Path temp;
    static final String REFUND = "# 환불 정책\n\n## 접수 기한\n\n이중 결제 환불 접수 기한은 결제일로부터 45일이다.\n\n"
            + "## 확인 절차\n\n소유권 미확인 시 중단하고 이관한다.\n\n## 승인\n\n실행에는 사람 승인이 필요하다.";
    static Document doc(String id, String body, String tenant) {
        return new Document(id, body, id + ".md", "current", tenant, true, "2", id, null, false);
    }
    static List<Document> documents() {
        return List.of(doc("refund-policy", REFUND, "public"),
                doc("account-unlock-policy", "# 계정 정책\n\n## 잠금 해제\n\n본인 확인 후 계정 잠금 해제 방법을 안내한다.", "public"),
                doc("private", REFUND, "tenant-beta"));
    }
    static final Map<String, String> SETTINGS = Map.of("AI_AX_LIVE", "1", "OPENAI_API_KEY", "fixture-only",
            "OPENAI_EMBEDDING_MODEL", "fixture-model", "OPENAI_MODEL", "fixture-generator");
    static float[] vector(String text) {
        return text.contains("환불") || text.contains("두 번") ? new float[]{1, 0} : new float[]{0, 1};
    }
    RagApplication.Dependencies deps(List<Document> docs, EmbeddingIndex.Embedder embedder, Quickstart.Generator generator) {
        return new RagApplication.Dependencies(path -> docs, SETTINGS::get, model -> embedder, generator);
    }
    Path prepare(List<Document> docs) throws Exception {
        Path file = temp.resolve("index.json");
        EmbeddingIndex.build(SectionChunking.split(docs, RagApplication.TENANT), file, RagApplication.TENANT,
                "fixture-model", texts -> texts.stream().map(SearchComparisonTest::vector).toList());
        return file;
    }
    SearchComparison.Options live(Path file, boolean answer) {
        return SearchComparison.Options.parse(answer ? new String[]{"--live", "--answer", "--index", file.toString()}
                : new String[]{"--live", "--index", file.toString()});
    }

    @Test void existingSectionSnapshotIsReusedAndOnlyThreeQueriesAreEmbedded() throws Exception {
        Path file = prepare(documents());
        byte[] before = Files.readAllBytes(file);
        var calls = new ArrayList<List<String>>();
        var dependencies = deps(documents(), texts -> {
            calls.add(List.copyOf(texts));
            return texts.stream().map(SearchComparisonTest::vector).toList();
        }, (q, s) -> { fail("Generated during retrieval comparison"); return null; });
        var report = SearchComparison.execute(live(file, false), dependencies);
        var tree = Quickstart.JSON.valueToTree(report);
        assertEquals("COMPARISON_READY", tree.path("status").asText());
        assertEquals(SearchComparison.CASES.stream().map(c -> List.of(c.query())).toList(), calls);
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(6, tree.path("chunk_count").asInt());
        assertEquals(2, tree.path("document_count").asInt());
        var allowed = SectionChunking.split(documents(), RagApplication.TENANT).stream().map(Chunk::chunkId).toList();
        for (var item : tree.path("cases")) {
            assertEquals(3, item.path("results").size());
            for (var method : item.path("results")) {
                for (var hit : method.path("candidates")) {
                    assertTrue(allowed.contains(hit.path("chunk_id").asText()));
                    assertFalse(hit.path("text").asText().isEmpty());
                }
                assertFalse(method.path("answer_result").path("model_called").asBoolean());
            }
            for (var contribution : item.path("results").path("hybrid").path("rrf_contributions"))
                assertEquals(contribution.path("bm25_contribution").asDouble() + contribution.path("embedding_contribution").asDouble(),
                        contribution.path("rrf_score").asDouble(), 1e-12);
        }
        Path reportFile = SearchComparison.saveReport(report, temp.resolve("reports"));
        assertEquals(Quickstart.JSON.readTree(Quickstart.json(report)), Quickstart.JSON.readTree(Files.readString(reportFile)));
    }

    @Test void selectionCutsAtFourChunksAndTwoDocumentsWithoutParentExpansion() {
        var chunks = SectionChunking.split(documents(), RagApplication.TENANT);
        var third = SectionChunking.split(List.of(doc("third", "제삼 문서", "public")), RagApplication.TENANT).get(0);
        var hits = List.of(new RetrievedChunk(chunks.get(1), .99), new RetrievedChunk(chunks.get(5), .98),
                new RetrievedChunk(third, .97), new RetrievedChunk(chunks.get(2), .96), new RetrievedChunk(chunks.get(3), .95));
        var result = SearchComparison.evidence("환불 기한과 계정 잠금 해제", hits, (query, sources) -> {
            assertEquals(Set.of("refund-policy", "account-unlock-policy"), sources.keySet());
            assertTrue(sources.get("refund-policy").contains("45일"));
            assertTrue(sources.get("refund-policy").contains("소유권"));
            assertFalse(sources.get("refund-policy").contains("사람 승인"));
            return Map.of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.copyOf(sources.keySet()));
        });
        var tree = Quickstart.JSON.valueToTree(result);
        assertEquals(5, tree.path("candidates").size());
        assertEquals("ANSWERED", tree.path("answer_result").path("status").asText());
        assertTrue(tree.path("answer_result").path("model_called").asBoolean());
    }

    @Test void emptyEvidenceSkipsGenerationAndMissingRanksContributeZero() {
        var result = SearchComparison.evidence("없음", List.of(), (q, s) -> { fail("Generated without evidence"); return null; });
        assertEquals("ABSTAINED", Quickstart.JSON.valueToTree(result).path("answer_result").path("status").asText());
        var hit = new RetrievedChunk(SectionChunking.split(documents(), RagApplication.TENANT).get(1), 999);
        var fused = HybridSearch.fuse(List.of(hit), List.of(), 8);
        var row = SearchComparison.contributions(List.of(hit), List.of(), fused).get(0);
        assertNull(row.get("embedding_rank"));
        assertEquals(0.0, row.get("embedding_contribution"));
        assertEquals(1.0 / 61, (Double) row.get("rrf_score"), 1e-12);
    }

    @Test void staleOrMissingSnapshotCannotEmbedQueriesOrGenerate() throws Exception {
        Path file = prepare(documents());
        var changed = List.of(doc("refund-policy", REFUND.replace("45일", "60일"), "public"));
        var dependencies = deps(changed, texts -> { fail("Embedded stale index query"); return null; },
                (q, s) -> { fail("Generated with stale index"); return null; });
        assertEquals("INDEX_OUTDATED", SearchComparison.execute(live(file, true), dependencies).get("status"));
        assertEquals("INDEX_REQUIRED", SearchComparison.execute(live(temp.resolve("missing.json"), true), dependencies).get("status"));
    }

    @Test void allThreeMethodsPassTheirSelectedEvidenceToGenerator() throws Exception {
        Path file = prepare(documents());
        var requests = new ArrayList<String>();
        var dependencies = deps(documents(), texts -> texts.stream().map(SearchComparisonTest::vector).toList(), (q, sources) -> {
            assertFalse(sources.isEmpty());
            assertTrue(sources.size() <= 2);
            requests.add(q);
            return Map.of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.copyOf(sources.keySet()));
        });
        var tree = Quickstart.JSON.valueToTree(SearchComparison.execute(live(file, true), dependencies));
        assertEquals("COMPARISON_READY", tree.path("status").asText());
        assertEquals(9, requests.size());
        for (var item : tree.path("cases"))
            for (var method : item.path("results"))
                assertEquals("ANSWERED", method.path("answer_result").path("status").asText());
    }

    @Test void providedPoliciesRunActualBm25WithoutReadingConfiguration() throws Exception {
        var docs = Chunking.loadDocuments(Quickstart.KNOWLEDGE_BASE);
        var dependencies = new RagApplication.Dependencies(path -> docs,
                name -> { fail("Read real settings"); return null; },
                model -> { fail("Created real provider"); return null; },
                (q, s) -> { fail("Generated in BM25-only run"); return null; });
        var report = SearchComparison.execute(SearchComparison.Options.parse(new String[]{}), dependencies);
        var tree = Quickstart.JSON.valueToTree(report);
        assertEquals("COMPARISON_READY", tree.path("status").asText());
        assertEquals("BM25_ONLY", tree.path("mode").asText());
        assertEquals(3, tree.path("cases").size());
        assertEquals("refund-policy", tree.path("cases").get(0).path("results").path("bm25").path("candidates").get(0).path("document_id").asText());
        System.out.println("PROVIDED_BM25 " + Quickstart.json(report));
    }

    @Test void invalidComparisonFlagsRouteThroughExistingEntryWithoutDependencies() {
        var dependencies = new RagApplication.Dependencies(path -> { fail("Read files"); return null; },
                name -> { fail("Read settings"); return null; }, null, null);
        var output = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RagApplication.run(new String[]{"--compare", "--live", "--min-score", "0.67"}, dependencies);
        } finally { System.setOut(previous); }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("INVALID_INPUT"));
        assertThrows(IllegalArgumentException.class, () -> SearchComparison.Options.parse(new String[]{"--answer"}));
    }

    @Test void parentExpansionKeepsRanksAndDocumentsWhileAddingMissingSections() {
        var chunks = SectionChunking.split(documents(), RagApplication.TENANT);
        var third = SectionChunking.split(List.of(doc("third", "제삼 문서", "public")), RagApplication.TENANT).get(0);
        var allowed = new ArrayList<>(chunks);
        allowed.add(third);
        var hits = List.of(new RetrievedChunk(chunks.get(1), .99), new RetrievedChunk(chunks.get(4), .98),
                new RetrievedChunk(third, .97), new RetrievedChunk(chunks.get(2), .96), new RetrievedChunk(chunks.get(3), .95));
        var baseline = Quickstart.JSON.valueToTree(SearchComparison.evidence("환불과 잠금", hits));
        var expanded = Quickstart.JSON.valueToTree(SearchComparison.evidence("환불과 잠금", hits, allowed, true, (q, sources) -> {
            assertEquals(Set.of("refund-policy", "account-unlock-policy"), sources.keySet());
            assertEquals(REFUND, sources.get("refund-policy"));
            assertEquals(documents().get(1).text(), sources.get("account-unlock-policy"));
            return Map.of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.copyOf(sources.keySet()));
        }));
        assertEquals(baseline.path("candidates"), expanded.path("candidates"));
        assertEquals(baseline.path("answer_result").path("sources"), expanded.path("selected_source_texts"));
        assertEquals(baseline.path("evidence").path("citations"), expanded.path("evidence").path("citations"));
        assertFalse(baseline.path("answer_result").path("sources").path("refund-policy").asText().contains("사람 승인"));
        assertFalse(baseline.path("answer_result").path("sources").path("account-unlock-policy").asText().contains("본인 확인"));
        assertTrue(expanded.path("answer_result").path("sources").path("refund-policy").asText().contains("사람 승인"));
        assertTrue(expanded.path("answer_result").path("sources").path("account-unlock-policy").asText().contains("본인 확인"));
        assertEquals("ANSWERED", expanded.path("answer_result").path("status").asText());
    }

    @Test void allMethodsExpandAllowedParentsWithoutReindexingOrChangingRetrieval() throws Exception {
        var docs = new ArrayList<>(documents());
        docs.add(new Document("old", "오래된 환불 자료", "old.md", "archived", "public", true, "1", "refund", null, false));
        docs.add(new Document("untrusted", "불신 환불 자료", "untrusted.md", "current", "public", false, "1", "refund", null, false));
        Path file = prepare(docs);
        byte[] snapshot = Files.readAllBytes(file);
        String query = "환불 계정 잠금";
        var calls = new ArrayList<List<String>>();
        var generated = new ArrayList<Map<String, String>>();
        var dependencies = deps(docs, texts -> {
            calls.add(List.copyOf(texts));
            return texts.stream().map(SearchComparisonTest::vector).toList();
        }, (q, sources) -> {
            assertEquals(query, q);
            assertFalse(sources.isEmpty());
            for (var source : sources.entrySet()) {
                assertEquals(documents().stream().filter(d -> d.documentId().equals(source.getKey()))
                        .findFirst().orElseThrow().text(), source.getValue());
            }
            generated.add(Map.copyOf(sources));
            return Map.of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.copyOf(sources.keySet()));
        });
        var baseline = Quickstart.JSON.valueToTree(SearchComparison.execute(SearchComparison.Options.parse(
                new String[]{"--live", "--index", file.toString(), "--query", query}), dependencies));
        var report = SearchComparison.execute(SearchComparison.Options.parse(
                new String[]{"--live", "--answer", "--expand-parent", "--index", file.toString(), "--query", query}), dependencies);
        var expanded = Quickstart.JSON.valueToTree(report);
        assertEquals("COMPARISON_READY", expanded.path("status").asText());
        assertEquals("SELECTED_CHUNKS", baseline.path("settings").path("context").asText());
        assertEquals("PARENT_DOCUMENT", expanded.path("settings").path("context").asText());
        assertEquals(List.of(List.of(query), List.of(query)), calls);
        assertEquals(3, generated.size());
        assertArrayEquals(snapshot, Files.readAllBytes(file));
        for (String method : List.of("bm25", "embedding", "hybrid")) {
            var before = baseline.path("cases").get(0).path("results").path(method);
            var after = expanded.path("cases").get(0).path("results").path(method);
            assertEquals(before.path("candidates"), after.path("candidates"));
            assertEquals(before.path("answer_result").path("sources"), after.path("selected_source_texts"));
            assertEquals(before.path("answer_result").path("source_ids"), after.path("answer_result").path("source_ids"));
            assertFalse(after.path("answer_result").path("sources").has("private"));
            assertFalse(after.path("answer_result").path("sources").has("old"));
            assertFalse(after.path("answer_result").path("sources").has("untrusted"));
        }
        Path saved = SearchComparison.saveReport(report, temp.resolve("reports"));
        assertTrue(saved.getFileName().toString().contains("PARENT_DOCUMENT"));
        assertEquals(Quickstart.JSON.readTree(Quickstart.json(report)), Quickstart.JSON.readTree(Files.readString(saved)));
    }

    @Test void parentExpansionPreservesEmptyAndConflictingEvidenceAbstention() {
        Quickstart.Generator noGeneration = (q, sources) -> { fail("Generated despite abstention"); return null; };
        var empty = Quickstart.JSON.valueToTree(SearchComparison.evidence("없음", List.of(), List.of(), true, noGeneration));
        assertEquals("ABSTAINED", empty.path("answer_result").path("status").asText());
        assertTrue(empty.path("answer_result").path("sources").isEmpty());
        var a = new Chunk("a-1", "a", "환불 30일", "a.md", "current", "public", true, "1", "refund", "b", false);
        var b = new Chunk("b-1", "b", "환불 45일", "b.md", "current", "public", true, "1", "refund", "a", false);
        var hits = List.of(new RetrievedChunk(a, 1), new RetrievedChunk(b, .9));
        var conflict = Quickstart.JSON.valueToTree(SearchComparison.evidence("환불 기한", hits, List.of(a, b), true, noGeneration));
        assertEquals("ABSTAINED", conflict.path("answer_result").path("status").asText());
        assertEquals("CONFLICTING_SOURCES", conflict.path("evidence").path("failure_reason").asText());
        assertFalse(conflict.path("answer_result").path("model_called").asBoolean());
    }

}
