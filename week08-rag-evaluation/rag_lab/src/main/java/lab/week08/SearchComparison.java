package lab.week08;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static lab.week08.Models.*;

/** 기존 절 단위 색인에서 같은 질문의 BM25·임베딩·RRF 순위를 비교합니다. */
public final class SearchComparison {
    static final int CANDIDATES = 8;
    static final int CONTEXT_CHUNKS = 4;
    record Case(String id, String query) {}
    static final List<Case> CASES = List.of(
            new Case("exact", "이중 결제 환불 접수 기한은 며칠인가요?"),
            new Case("paraphrase", "같은 요금이 두 번 나갔어요. 언제까지 돌려달라고 접수할 수 있나요?"),
            new Case("multiple-topics", "환불 접수 기한과 계정 잠금 해제 방법을 알려 주세요"));

    record Options(boolean live, boolean answer, boolean expandParent, Path kb, Path index, List<Case> cases) {
        static Options parse(String[] args) {
            var flags = new HashSet<String>();
            var values = new HashMap<String, String>();
            for (int i = 0; i < args.length; i++) {
                String name = args[i];
                if (Set.of("--live", "--answer", "--expand-parent").contains(name)) {
                    if (!flags.add(name)) throw new IllegalArgumentException("Duplicate option");
                } else if (Set.of("--kb", "--index", "--query").contains(name)) {
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")
                            || values.putIfAbsent(name, args[i]) != null)
                        throw new IllegalArgumentException("Missing or duplicate value");
                } else throw new IllegalArgumentException("Unknown option");
            }
            if (flags.contains("--answer") && !flags.contains("--live"))
                throw new IllegalArgumentException("Generation requires live comparison");
            return new Options(flags.contains("--live"), flags.contains("--answer"), flags.contains("--expand-parent"),
                    Path.of(values.getOrDefault("--kb", "../knowledge_base")),
                    Path.of(values.getOrDefault("--index", ".local/rag-sections-index.json")),
                    values.containsKey("--query") ? List.of(new Case("custom", values.get("--query"))) : CASES);
        }
    }

    static Map<String, Object> evidence(String query, List<RetrievedChunk> hits) {
        return evidence(query, hits, null);
    }

    static Map<String, Object> evidence(String query, List<RetrievedChunk> hits, Quickstart.Generator generator) {
        return evidence(query, hits, List.of(), false, generator);
    }

    static Map<String, Object> evidence(String query, List<RetrievedChunk> hits, List<Chunk> allowedChunks,
                                        boolean expandParent, Quickstart.Generator generator) {
        var context = hits.stream().limit(CONTEXT_CHUNKS).map(h -> new RankedChunk(h.chunk(), h.lexicalScore(),
                h.lexicalScore(), List.of("검색 순위 유지"))).toList();
        var evidence = Answering.answer(query, context);
        @SuppressWarnings("unchecked")
        var selectedSources = new LinkedHashMap<>((Map<String, String>) evidence.get("source_texts"));
        if (expandParent && !"ABSTAINED".equals(evidence.get("status"))) {
            var expanded = new LinkedHashMap<String, String>();
            for (String id : selectedSources.keySet()) {
                var hit = context.stream().map(RankedChunk::chunk)
                        .filter(c -> c.documentId().equals(id)).findFirst().orElseThrow();
                expanded.putAll(ContextChoices.parentDocument(hit, allowedChunks));
            }
            evidence.put("source_texts", expanded);
        }
        evidence.put("retrieved_document_ids", hits.stream().map(h -> h.chunk().documentId()).toList());
        var candidates = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < hits.size(); i++) {
            var h = hits.get(i);
            candidates.add(Map.of("rank", i + 1, "chunk_id", h.chunk().chunkId(),
                    "document_id", h.chunk().documentId(), "score", h.lexicalScore(), "text", h.chunk().text()));
        }
        return Map.of("candidates", candidates, "selected_source_texts", selectedSources, "evidence", evidence,
                "answer_result", Quickstart.finish(query, evidence, generator));
    }

    static List<Map<String, Object>> contributions(List<RetrievedChunk> lexical, List<RetrievedChunk> semantic,
                                                  List<RetrievedChunk> fused) {
        var bmRanks = ranks(lexical);
        var embeddingRanks = ranks(semantic);
        return fused.stream().map(hit -> {
            String id = hit.chunk().chunkId();
            var row = new LinkedHashMap<String, Object>();
            row.put("chunk_id", id);
            row.put("bm25_rank", bmRanks.get(id));
            row.put("embedding_rank", embeddingRanks.get(id));
            row.put("bm25_contribution", bmRanks.containsKey(id) ? 1.0 / (60 + bmRanks.get(id)) : 0.0);
            row.put("embedding_contribution", embeddingRanks.containsKey(id) ? 1.0 / (60 + embeddingRanks.get(id)) : 0.0);
            row.put("rrf_score", hit.lexicalScore());
            return (Map<String, Object>) row;
        }).toList();
    }

    private static Map<String, Integer> ranks(List<RetrievedChunk> hits) {
        var result = new LinkedHashMap<String, Integer>();
        for (var hit : hits) result.putIfAbsent(hit.chunk().chunkId(), result.size() + 1);
        return result;
    }

    static Map<String, Object> execute(Options options, RagApplication.Dependencies deps) {
        String model = null;
        if (options.live()) {
            try {
                if (!RagApplication.required(deps, "AI_AX_LIVE").equals("1")) throw new IllegalArgumentException();
                RagApplication.required(deps, "OPENAI_API_KEY");
                model = RagApplication.required(deps, "OPENAI_EMBEDDING_MODEL");
                if (options.answer()) RagApplication.required(deps, "OPENAI_MODEL");
            } catch (IllegalArgumentException e) {
                return RagApplication.error("CONFIGURATION_REQUIRED", "기존 IDE의 임베딩 설정을 확인하세요. 답변 생성에는 OPENAI_MODEL도 필요합니다.", "CONFIGURATION");
            }
        }
        final List<Chunk> chunks;
        try {
            chunks = SectionChunking.split(deps.documents().load(options.kb()), RagApplication.TENANT);
            if (chunks.isEmpty()) throw new IllegalArgumentException();
        } catch (IOException | IllegalArgumentException e) {
            return RagApplication.error("DOCUMENT_ERROR", "현재 허용 정책과 관리 메타데이터를 확인하세요.", "DOCUMENTS");
        }
        EmbeddingIndex index = null;
        if (options.live()) {
            try {
                index = EmbeddingIndex.open(chunks, options.index(), RagApplication.TENANT, model, deps.embeddings().apply(model));
            } catch (NoSuchFileException e) {
                return RagApplication.error("INDEX_REQUIRED", "RagApplication의 --prepare로 절 단위 색인을 준비하세요.", "INDEX");
            } catch (IllegalArgumentException e) {
                return RagApplication.error("INDEX_OUTDATED", "현재 자료·절·모델과 색인이 다릅니다. RagApplication의 --prepare로 다시 준비하세요.", "INDEX");
            } catch (IOException | RuntimeException e) {
                return RagApplication.error("INDEX_ERROR", "절 단위 색인과 임베딩 연결을 확인하세요.", "INDEX");
            }
        }
        var cases = new ArrayList<Map<String, Object>>();
        Quickstart.Generator generator = options.answer() ? deps.generator() : null;
        try (var bm25 = new Bm25Index(chunks, RagApplication.TENANT)) {
            for (var item : options.cases()) {
                var results = new LinkedHashMap<String, Object>();
                var lexical = bm25.retrieve(item.query(), CANDIDATES);
                // Complete retrieval before optional generation, using one query vector for both semantic and hybrid.
                var semantic = index == null ? null : index.retrieve(item.query(), CANDIDATES, 0.0);
                results.put("bm25", evidence(item.query(), lexical, chunks, options.expandParent(), generator));
                if (semantic != null) {
                    var fused = HybridSearch.fuse(lexical, semantic, CANDIDATES);
                    results.put("embedding", evidence(item.query(), semantic, chunks, options.expandParent(), generator));
                    var hybrid = new LinkedHashMap<>(evidence(item.query(), fused, chunks, options.expandParent(), generator));
                    hybrid.put("rrf_contributions", contributions(lexical, semantic, fused));
                    results.put("hybrid", hybrid);
                }
                cases.add(Map.of("case_id", item.id(), "query", item.query(), "results", results));
            }
        } catch (IOException e) {
            return RagApplication.error("SEARCH_ERROR", "BM25 색인과 검색 처리를 확인하세요.", "RETRIEVAL");
        } catch (RuntimeException e) {
            var error = RagApplication.providerError(e, "검색 비교를 마치지 못했습니다.", "RETRIEVAL");
            error.put("completed_cases", cases);
            return error;
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "COMPARISON_READY");
        report.put("mode", options.live() ? "REAL_EMBEDDING" : "BM25_ONLY");
        report.put("answer_generation", options.answer() ? "REQUESTED" : "NOT_RUN");
        if (model != null) report.put("embedding_model", model);
        report.put("settings", Map.of("candidate_limit", CANDIDATES, "context_chunk_limit", CONTEXT_CHUNKS,
                "document_limit", 2, "embedding_min_score", 0.0, "rerank", false, "chunking", "MARKDOWN_SECTIONS",
                "context", options.expandParent() ? "PARENT_DOCUMENT" : "SELECTED_CHUNKS", "tenant", RagApplication.TENANT, "status_filter", "current"));
        report.put("chunk_count", chunks.size());
        report.put("document_count", chunks.stream().map(Chunk::documentId).distinct().count());
        report.put("cases", cases);
        return report;
    }

    static Path saveReport(Map<String, Object> report, Path directory) throws IOException {
        Files.createDirectories(directory);
        String context = Quickstart.JSON.valueToTree(report).path("settings").path("context").asText();
        Path file = Files.createTempFile(directory, "search-comparison-" + report.get("mode") + "-" + context + "-", ".json");
        Files.writeString(file, Quickstart.json(report));
        return file;
    }

    public static void run(String[] args, RagApplication.Dependencies deps) {
        if (Arrays.equals(args, new String[]{"--help"})) {
            System.out.println("SearchComparison: [--live [--answer]] [--expand-parent] [--query TEXT] [--kb DIRECTORY] [--index FILE]\n"
                    + "기본은 세 질문의 BM25 검색입니다. --live는 기존 절 색인으로 세 방식을 비교합니다.\n"
                    + "--expand-parent는 선택된 문서의 같은 버전·접근 범위 원문으로 생성 근거를 확장합니다.");
            return;
        }
        final Options options;
        try { options = Options.parse(args); }
        catch (IllegalArgumentException e) {
            System.out.println(Quickstart.json(RagApplication.error("INVALID_INPUT", "--compare --help로 비교 실행 인자를 확인하세요.", "INPUT")));
            return;
        }
        System.out.println("검색 비교를 실행합니다. 완료 후 후보 본문과 선택 근거를 JSON으로 저장합니다.");
        var report = execute(options, deps);
        if (!"COMPARISON_READY".equals(report.get("status"))) {
            System.out.println(Quickstart.json(report));
            return;
        }
        var tree = Quickstart.JSON.valueToTree(report);
        System.out.println(report.get("mode") + " | 후보 8 / 상위 4절 / 최대 2문서 / 재정렬 없음");
        System.out.println("문맥: " + (options.expandParent() ? "선택 문서의 원문으로 확장" : "선택 조각만 전달"));
        for (var item : tree.path("cases")) {
            System.out.println("\n" + item.path("case_id").asText() + " | " + item.path("query").asText());
            item.path("results").fields().forEachRemaining(entry -> {
                var value = entry.getValue();
                var answer = value.path("answer_result");
                System.out.println("  " + entry.getKey() + " | " + answer.path("status").asText());
                for (var hit : value.path("candidates")) {
                    if (hit.path("rank").asInt() > CONTEXT_CHUNKS) break;
                    System.out.printf(Locale.ROOT, "    %d. %s | %.6f%n", hit.path("rank").asInt(),
                            hit.path("chunk_id").asText(), hit.path("score").asDouble());
                }
                var sourceDocuments = new ArrayList<String>();
                answer.path("sources").fieldNames().forEachRemaining(sourceDocuments::add);
                System.out.println("    전달 근거 문서: " + sourceDocuments);
                if (options.answer()) System.out.println("    답변 출처: " + answer.path("source_ids"));
                if (options.answer()) System.out.println("    답변: " + answer.path("answer").asText());
            });
        }
        try { System.out.println("\n전체 결과 파일: " + saveReport(report, Path.of(".local"))); }
        catch (IOException e) {
            System.out.println("결과 파일을 저장하지 못했습니다. 전체 결과를 아래에 출력합니다.");
            System.out.println(Quickstart.json(report));
        }
    }

    public static void main(String[] args) { run(args, RagApplication.Dependencies.live()); }
}
