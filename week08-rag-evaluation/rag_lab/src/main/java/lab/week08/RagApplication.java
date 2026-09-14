package lab.week08;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import static lab.week08.Models.*;

/** Current-policy RAG with section retrieval and full permitted document context. */
public final class RagApplication {
    public static final String TENANT = "tenant-alpha";
    public static final int CANDIDATES = 8;
    public static final int DOCUMENTS = 2;

    @FunctionalInterface public interface DocumentLoader { List<Document> load(Path directory) throws IOException; }
    public record Dependencies(DocumentLoader documents, Function<String, String> environment,
                               Function<String, EmbeddingIndex.Embedder> embeddings, Quickstart.Generator generator) {
        public static Dependencies live() {
            // Only the learner's IDE entry point selects real configuration and provider adapters.
            return new Dependencies(Chunking::loadDocuments, System::getenv, EmbeddingIndex::live, Quickstart::generateLive);
        }
    }

    public record Options(Path knowledgeBase, Path indexFile, boolean semantic, boolean prepare,
                          boolean evaluate, boolean redTeam, boolean generate, String query, double minScore) {
        public static Options parse(String[] args) {
            var flags = new HashSet<String>();
            var values = new HashMap<String, String>();
            var switches = Set.of("--semantic", "--lexical", "--prepare", "--evaluate", "--red-team", "--generate", "--retrieve-only");
            var valueNames = Set.of("--kb", "--index", "--query", "--min-score");
            for (int i = 0; i < args.length; i++) {
                String name = args[i];
                if (switches.contains(name)) {
                    if (!flags.add(name)) throw new IllegalArgumentException("Duplicate option");
                } else if (valueNames.contains(name)) {
                    if (++i == args.length || args[i].startsWith("--") || values.putIfAbsent(name, args[i]) != null)
                        throw new IllegalArgumentException("Missing or duplicate option value");
                } else throw new IllegalArgumentException("Unknown option");
            }
            if (flags.contains("--semantic") && flags.contains("--lexical")
                    || flags.contains("--generate") && flags.contains("--retrieve-only")
                    || flags.contains("--evaluate") && values.containsKey("--query")
                    || flags.contains("--prepare") && (flags.contains("--evaluate") || flags.contains("--generate")
                        || flags.contains("--retrieve-only") || flags.contains("--lexical") || values.containsKey("--query")
                        || values.containsKey("--min-score")))
                throw new IllegalArgumentException("Incompatible options");
            if (flags.contains("--red-team") && (!flags.contains("--generate") || !values.isEmpty()
                    || flags.stream().anyMatch(flag -> !Set.of("--red-team", "--generate").contains(flag))))
                throw new IllegalArgumentException("Red Team requires generation without retrieval options");
            boolean semantic = flags.contains("--semantic") || flags.contains("--prepare");
            double threshold = Double.parseDouble(values.getOrDefault("--min-score", semantic ? "0.6" : "0.08"));
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("Score must be between zero and one");
            String query = values.getOrDefault("--query", "이중 결제 환불 접수 기한은 며칠인가요?");
            if (query.isBlank()) throw new IllegalArgumentException("Query is empty");
            return new Options(Path.of(values.getOrDefault("--kb", "../knowledge_base")),
                    Path.of(values.getOrDefault("--index", ".local/rag-sections-index.json")), semantic,
                    flags.contains("--prepare"), flags.contains("--evaluate"), flags.contains("--red-team"), flags.contains("--generate"), query, threshold);
        }
    }

    private final Options options;
    private final List<Chunk> chunks;
    private final EmbeddingIndex index;
    private final Quickstart.Generator generator;

    public RagApplication(Options options, List<Document> documents, EmbeddingIndex index, Quickstart.Generator generator) {
        this.options = options;
        this.chunks = SectionChunking.split(documents, TENANT);
        this.index = index;
        this.generator = generator;
    }

    public Map<String, Object> answer(String query) {
        final List<RetrievedChunk> retrieved;
        try {
            retrieved = options.semantic() ? Objects.requireNonNull(index).retrieve(query, CANDIDATES, options.minScore())
                    : Retrieval.retrieve(query, chunks, TENANT, CANDIDATES, options.minScore(), false, false);
        } catch (RuntimeException e) {
            var result = providerError(e, "검색 호출에 실패했습니다.", "RETRIEVAL");
            result.put("query", query);
            result.putAll(settings());
            return result;
        }
        // Keep retrieval order: no rule bonus or intermediate four-section cutoff.
        var ranked = retrieved.stream().map(r -> new RankedChunk(r.chunk(), r.lexicalScore(), r.lexicalScore(), List.of("retrieval-score"))).toList();
        var evidence = Answering.answer(query, ranked);
        @SuppressWarnings("unchecked") var selected = (Map<String, String>) evidence.get("source_texts");
        var expanded = new LinkedHashMap<String, String>();
        for (String id : selected.keySet()) {
            var hit = retrieved.stream().map(RetrievedChunk::chunk).filter(c -> c.documentId().equals(id)).findFirst().orElseThrow();
            // All current permitted sections, in original order, not just the search hits.
            expanded.putAll(ContextChoices.parentDocument(hit, chunks));
        }
        evidence.put("source_texts", expanded);
        evidence.put("retrieved_document_ids", retrieved.stream().map(r -> r.chunk().documentId()).toList());
        var result = finishEvidence(query, evidence);
        result.put("retrieved_chunks", retrieved.stream().map(r -> Map.of("chunk_id", r.chunk().chunkId(),
                "document_id", r.chunk().documentId(), "score", r.lexicalScore(), "text", r.chunk().text())).toList());
        result.putAll(settings());
        return result;
    }

    /** Accept supplied evaluation evidence through the same generation and validation boundary. */
    public Map<String, Object> answerFromSources(String query, Map<String, String> sources) {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("status", sources.isEmpty() ? "ABSTAINED" : "ANSWERED");
        evidence.put("source_texts", sources);
        // No search ran, so supplied document IDs are not retrieval candidates.
        evidence.put("retrieved_document_ids", List.of());
        evidence.put("answer", "확인할 수 있는 근거 문서가 없어 답변을 보류합니다.");
        if (sources.isEmpty()) evidence.put("failure_reason", "NO_EVIDENCE");
        var result = finishEvidence(query, evidence);
        result.put("retrieval_method", "DIRECT_EVIDENCE");
        return result;
    }

    private Map<String, Object> finishEvidence(String query, Map<String, Object> evidence) {
        var result = Quickstart.finish(query, evidence, options.generate() ? generator : null);
        if (result.get("status").equals("EVIDENCE_READY"))
            result.put("answer", "근거가 준비되었습니다. sources의 정책 본문에서 질문에 필요한 조건을 확인하세요.");
        if (evidence.get("failure_reason") != null)
            result.put("failure_reason", evidence.get("failure_reason"));
        result.put("answer_kind", Boolean.TRUE.equals(result.get("model_called")) ? "GENERATION_RESULT" : "NOTICE");
        return result;
    }

    private Map<String, Object> settings() {
        return Map.of("retrieval_method", options.semantic() ? "EMBEDDING_STORE" : "LEXICAL_BASELINE",
                "retrieval_settings", Map.of("min_score", options.minScore(), "candidate_limit", CANDIDATES,
                        "document_limit", DOCUMENTS, "rerank", false, "chunking", "MARKDOWN_SECTIONS",
                        "context", "PARENT_DOCUMENT", "tenant", TENANT, "status_filter", "current"));
    }

    private static Map<String, Object> error(String status, String message, String stage) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status); result.put("answer", message); result.put("failure_stage", stage);
        result.put("model_called", false); result.put("mode", "RETRIEVAL_ONLY");
        result.put("sources", Map.of()); result.put("source_ids", List.of()); result.put("retrieved_document_ids", List.of());
        return result;
    }

    private static Map<String, Object> providerError(RuntimeException exception, String message, String stage) {
        var result = error("PROVIDER_ERROR", message, stage);
        String reason = "UNEXPECTED_ERROR";
        if (exception instanceof com.openai.errors.OpenAIServiceException service) {
            int status = service.statusCode();
            result.put("http_status", status);
            // Only known categories leave this boundary; never print bodies, headers or exception messages.
            reason = switch (service.code().orElse("")) {
                case "credit_balance_exhausted" -> "CREDIT_BALANCE_EXHAUSTED";
                case "insufficient_quota", "billing_hard_limit_reached" -> "QUOTA_EXCEEDED";
                case "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
                        "organization_usage_limit_exceeded" -> "USAGE_LIMIT_EXCEEDED";
                case "rate_limit_exceeded", "slow_down" -> "RATE_LIMITED";
                default -> switch (status) {
                    case 401 -> "AUTHENTICATION_FAILED";
                    case 403 -> "ACCESS_DENIED";
                    case 404 -> "MODEL_OR_ENDPOINT_NOT_FOUND";
                    case 400, 422 -> "REQUEST_REJECTED";
                    case 429 -> "QUOTA_OR_RATE_LIMIT";
                    default -> status >= 500 ? "SERVICE_UNAVAILABLE" : "REQUEST_REJECTED";
                };
            };
        } else if (exception instanceof com.openai.errors.OpenAIIoException) {
            reason = "CONNECTION_ERROR";
        } else if (exception instanceof com.openai.errors.OpenAIInvalidDataException) {
            reason = "INVALID_PROVIDER_RESPONSE";
        }
        String guidance = switch (reason) {
            case "CREDIT_BALANCE_EXHAUSTED" -> "API Billing의 선불 크레딧 잔액을 확인하세요.";
            case "QUOTA_EXCEEDED", "QUOTA_OR_RATE_LIMIT" -> "API Billing 잔액과 Limits의 사용량·호출 한도를 확인하세요.";
            case "USAGE_LIMIT_EXCEEDED" -> "API 조직·프로젝트의 사용·지출 한도를 확인하세요.";
            case "RATE_LIMITED" -> "API 호출 속도 한도에 도달했습니다. 잠시 후 다시 실행하세요.";
            case "AUTHENTICATION_FAILED" -> "IDE에서 키와 해당 조직의 인증 설정을 직접 확인하세요. 키는 공유하지 마세요.";
            case "ACCESS_DENIED" -> "API 프로젝트·모델·지역의 접근 허용 여부를 확인하세요.";
            case "MODEL_OR_ENDPOINT_NOT_FOUND", "REQUEST_REJECTED" -> "임베딩 모델 ID·API 주소·요청 형식을 확인하세요.";
            case "CONNECTION_ERROR" -> "네트워크·프록시·인증서 또는 연결 시간 초과를 확인하세요.";
            case "SERVICE_UNAVAILABLE" -> "API 서비스의 일시적 오류입니다. 잠시 후 다시 실행하세요.";
            case "INVALID_PROVIDER_RESPONSE" -> "API 응답 형식과 SDK 연결을 확인해야 합니다.";
            default -> "연결 코드 또는 응답 처리에서 분류되지 않은 오류가 발생했습니다.";
        };
        result.put("failure_reason", reason);
        result.put("answer", message + " " + guidance);
        return result;
    }

    private static String required(Dependencies dependencies, String name) {
        String value = dependencies.environment().apply(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing configuration");
        return value;
    }

    public static void run(String[] args, Dependencies dependencies) {
        if (Arrays.equals(args, new String[]{"--help"})) {
            System.out.println("RagApplication: --red-team --generate | --prepare | [--evaluate | --query TEXT] [--lexical | --semantic] "
                    + "[--retrieve-only | --generate] [--min-score NUMBER] [--kb DIRECTORY] [--index FILE]");
            return;
        }
        final Options options;
        try { options = Options.parse(args); }
        catch (IllegalArgumentException e) {
            System.out.println(Quickstart.json(error("INVALID_INPUT", "실행 인자를 확인하세요. --help로 사용법을 볼 수 있습니다.", "INPUT"))); return;
        }
        String model = null;
        if (options.semantic() || options.generate()) {
            try {
                if (!required(dependencies, "AI_AX_LIVE").equals("1")) throw new IllegalArgumentException("Live calls disabled");
                required(dependencies, "OPENAI_API_KEY");
                if (options.semantic()) model = required(dependencies, "OPENAI_EMBEDDING_MODEL");
                if (options.generate()) required(dependencies, "OPENAI_MODEL");
            } catch (IllegalArgumentException e) {
                System.out.println(Quickstart.json(error("CONFIGURATION_REQUIRED",
                        "IDE의 AI_AX_LIVE=1, OPENAI_API_KEY와 실행에 필요한 OPENAI_EMBEDDING_MODEL 또는 OPENAI_MODEL을 설정하세요.", "CONFIGURATION"))); return;
            }
        }
        if (options.redTeam()) {
            var app = new RagApplication(options, List.of(), null, dependencies.generator());
            try { Evaluation.evaluateEvidence(Path.of("data/red-team.json"), app::answerFromSources); }
            catch (Exception e) {
                System.out.println(Quickstart.json(error("EVALUATION_ERROR", "평가 자료와 앱 연결을 확인하세요.", "EVALUATION")));
            }
            return;
        }
        final List<Document> documents;
        final List<Chunk> chunks;
        try {
            documents = dependencies.documents().load(options.knowledgeBase());
            chunks = SectionChunking.split(documents, TENANT);
            if (chunks.isEmpty()) throw new IllegalArgumentException("No current permitted sections");
        } catch (IOException | IllegalArgumentException e) {
            System.out.println(Quickstart.json(error("DOCUMENT_ERROR", "현재 허용 정책과 관리 메타데이터를 확인하세요.", "DOCUMENTS"))); return;
        }
        EmbeddingIndex index = null;
        if (options.semantic()) {
            try {
                var embedder = dependencies.embeddings().apply(model);
                if (options.prepare()) {
                    try { EmbeddingIndex.build(chunks, options.indexFile(), TENANT, model, embedder); }
                    catch (IOException e) {
                        System.out.println(Quickstart.json(error("INDEX_ERROR", "색인 파일을 저장하지 못했습니다.", "INDEX"))); return;
                    } catch (RuntimeException e) {
                        System.out.println(Quickstart.json(providerError(e, "문서 임베딩에 실패했습니다.", "EMBEDDING"))); return;
                    }
                    System.out.println(Quickstart.json(Map.of("status", "INDEX_READY", "chunk_count", chunks.size(),
                            "document_count", chunks.stream().map(Chunk::documentId).distinct().count(),
                            "chunking", "MARKDOWN_SECTIONS", "embedding_model", model)));
                    return;
                }
                index = EmbeddingIndex.open(chunks, options.indexFile(), TENANT, model, embedder);
            } catch (NoSuchFileException e) {
                System.out.println(Quickstart.json(error("INDEX_REQUIRED", "먼저 --prepare로 색인을 준비하세요.", "INDEX"))); return;
            } catch (IllegalArgumentException e) {
                System.out.println(Quickstart.json(error("INDEX_OUTDATED", "색인과 현재 자료·모델·접근 범위가 다릅니다. --prepare로 다시 준비하세요.", "INDEX"))); return;
            } catch (IOException e) {
                System.out.println(Quickstart.json(error("INDEX_ERROR", "색인 파일을 읽을 수 없습니다. --prepare로 다시 준비하세요.", "INDEX"))); return;
            } catch (RuntimeException e) {
                System.out.println(Quickstart.json(error("INDEX_ERROR", "색인 또는 임베딩 연결을 확인하세요.", "INDEX"))); return;
            }
        }
        var app = new RagApplication(options, documents, index, dependencies.generator());
        try {
            if (options.evaluate()) Evaluation.evaluate(Path.of("data/golden.json"), app::answer);
            else System.out.println(Quickstart.json(app.answer(options.query())));
        } catch (Exception e) {
            System.out.println(Quickstart.json(error("EVALUATION_ERROR", "평가 자료와 앱 연결을 확인하세요.", "EVALUATION")));
        }
    }

    public static void main(String[] args) { run(args, Dependencies.live()); }
}
