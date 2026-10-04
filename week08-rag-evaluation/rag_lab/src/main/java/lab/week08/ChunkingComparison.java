package lab.week08;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static lab.week08.Models.*;

/** 긴 문서 하나를 세 방식으로 나눠 같은 질문의 검색 후보와 모델에 전달할 본문을 비교합니다. */
public final class ChunkingComparison {
    static final Path EXAMPLE = Path.of("..", "chunking_example");
    static final String TENANT = "tenant-alpha";
    /** 질문과, 답에 필요한 조건을 원문에서 그대로 가져온 표현입니다. */
    record Case(String query, List<String> conditions) {}
    static final List<Case> CASES = List.of(
        new Case("구독 해지 환불에서 제외되는 경우는 무엇인가요?", List.of("할인 행사로 가입한 첫 결제 기간", "추가 저장 용량")),
        new Case("이중 결제 환불은 언제까지 접수할 수 있고 어떤 경우에 접수하지 않나요?", List.of("결제일로부터 30일", "이의 제기가 이미 접수된 경우")),
        new Case("서비스 장애 보상은 무엇으로 지급하나요?", List.of("이용권으로 지급")));

    /** 문단을 목표 크기까지 묶습니다. 제목 줄도 문단 하나로 취급합니다. */
    static List<Chunk> paragraphs(List<Document> documents) {
        return Chunking.splitDocuments(documents, 600, 80);
    }
    /** 본문이 있는 절마다 나누고 그 절의 제목 줄과 본문을 한 조각에 둡니다. 상위 제목은 조각에 남지 않습니다. */
    static List<Chunk> sections(List<Document> documents) { return bySection(documents, false); }
    /** 같은 절, 같은 조각 ID입니다. 첫 줄만 문서 제목부터 현재 절까지의 경로로 바꿉니다. */
    static List<Chunk> sectionsWithPath(List<Document> documents) { return bySection(documents, true); }

    /** 자료 폴더가 달라도 조각의 출처가 실제 파일 위치를 가리키게 합니다. */
    static List<Document> load(Path root) throws IOException {
        String folder = root.toAbsolutePath().normalize().getFileName().toString();
        return Chunking.loadDocuments(root).stream().map(d -> new Document(d.documentId(), d.text(),
            folder + d.source().substring(d.source().lastIndexOf('/')), d.status(), d.tenantId(), d.trusted(), d.version(),
            d.policyFamily(), d.conflictsWith(), d.fixtureOnly())).toList();
    }

    private static List<Chunk> bySection(List<Document> documents, boolean withPath) {
        var chunks = new ArrayList<Chunk>();
        for (var doc : documents) {
            var levels = new ArrayList<Integer>();
            var titles = new ArrayList<String>();
            var body = new StringBuilder();
            String heading = "";
            int index = 0;
            for (String line : (doc.text().replace("\r\n", "\n") + "\n# ").split("\n")) {
                if (!line.matches("#{1,6} .*")) { body.append(line).append('\n'); continue; }
                String text = body.toString().strip();
                body.setLength(0);
                // 본문이 없는 상위 제목은 두 방식 모두 조각으로 만들지 않습니다. 두 방식의 차이는 조각의 첫 줄뿐입니다.
                if (!text.isEmpty()) {
                    String first = withPath ? "절 경로: " + String.join(" > ", titles) : heading;
                    chunks.add(new Chunk(doc.documentId() + "#s" + String.format(Locale.ROOT, "%03d", index++), doc.documentId(),
                        (first + "\n\n" + text).strip(), doc.source(), doc.status(), doc.tenantId(), doc.trusted(), doc.version(),
                        doc.policyFamily(), doc.conflictsWith(), doc.fixtureOnly()));
                }
                // 제목 단계를 건너뛴 문서에서도 같은 단계의 절이 서로의 하위 절이 되지 않게 단계 숫자로 비교합니다.
                int level = line.indexOf(' ');
                while (!levels.isEmpty() && levels.get(levels.size() - 1) >= level) {
                    levels.remove(levels.size() - 1);
                    titles.remove(titles.size() - 1);
                }
                heading = line;
                levels.add(level);
                titles.add(line.substring(level + 1).strip());
            }
        }
        return chunks;
    }

    @FunctionalInterface interface Search { List<RetrievedChunk> find(String query, int topK) throws Exception; }

    /** 상위 후보의 본문을 이어 붙인 것이 모델에 전달할 근거입니다. 조건이 그 안에 남았는지 함께 표시합니다. */
    static Map<String, Object> result(Case item, Search search, int topK) throws Exception {
        var hits = search.find(item.query(), topK);
        String context = String.join("\n\n---\n\n", hits.stream().map(h -> h.chunk().text()).toList());
        var conditions = new LinkedHashMap<String, Boolean>();
        item.conditions().forEach(c -> conditions.put(c, context.contains(c)));
        var result = new LinkedHashMap<String, Object>();
        result.put("candidates", hits.stream().map(h -> {
            var candidate = new LinkedHashMap<String, Object>();
            candidate.put("chunk_id", h.chunk().chunkId());
            candidate.put("source", h.chunk().source());
            candidate.put("first_line", h.chunk().text().lines().findFirst().orElse(""));
            candidate.put("score", h.lexicalScore());
            return candidate;
        }).toList());
        result.put("conditions_in_context", conditions);
        result.put("context_chars", context.length());
        result.put("context", context);
        return result;
    }

    static Map<String, Object> sizes(List<Chunk> chunks) {
        var lengths = chunks.stream().mapToInt(c -> c.text().length()).summaryStatistics();
        var sizes = new LinkedHashMap<String, Object>();
        sizes.put("chunks", chunks.size());
        sizes.put("min_chars", lengths.getMin());
        sizes.put("average_chars", Math.round(lengths.getAverage()));
        sizes.put("max_chars", lengths.getMax());
        return sizes;
    }

    /** 잘못된 후보 수는 임베딩 호출과 색인 저장 전에 거부합니다. */
    static int topK(String[] args) {
        int topK = Integer.parseInt(Quickstart.option(args, "--top", "3"));
        if (topK < 1) throw new IllegalArgumentException("--top은 1 이상이어야 합니다.");
        return topK;
    }

    public static void main(String[] args) throws Exception {
        boolean live = Arrays.asList(args).contains("--live");
        int topK = topK(args);
        String custom = Quickstart.option(args, "--query", null);
        var cases = custom == null ? CASES : List.of(new Case(custom, List.of()));
        var documents = load(Path.of(Quickstart.option(args, "--docs", EXAMPLE.toString())));
        var strategies = new LinkedHashMap<String, List<Chunk>>();
        strategies.put("paragraphs_600_80", paragraphs(documents));
        strategies.put("sections", sections(documents));
        strategies.put("sections_with_path", sectionsWithPath(documents));
        var output = new LinkedHashMap<String, Object>();
        output.put("mode", live ? "REAL_EMBEDDING" : "BM25_ONLY");
        output.put("top_k", topK);
        for (var strategy : strategies.entrySet()) {
            var chunks = strategy.getValue();
            var report = new LinkedHashMap<String, Object>();
            report.put("sizes", sizes(chunks));
            var queries = new LinkedHashMap<String, Object>();
            // 방식마다 조각이 다르므로 색인도 따로 만듭니다. 기본 실습의 색인 파일은 건드리지 않습니다.
            String model = System.getenv("OPENAI_EMBEDDING_MODEL");
            var index = live ? EmbeddingIndex.build(chunks, Path.of(".local", "chunking-" + strategy.getKey() + ".json"),
                TENANT, model, EmbeddingIndex.live(model)) : null;
            try (var bm25 = new Bm25Index(chunks, TENANT)) {
                for (var item : cases) {
                    var searches = new LinkedHashMap<String, Object>();
                    searches.put("bm25", result(item, bm25::retrieve, topK));
                    if (live) searches.put("embedding", result(item, (query, k) -> index.retrieve(query, k, 0.0), topK));
                    queries.put(item.query(), searches);
                }
            }
            report.put("queries", queries);
            output.put(strategy.getKey(), report);
        }
        System.out.println(Quickstart.json(output));
    }
}
