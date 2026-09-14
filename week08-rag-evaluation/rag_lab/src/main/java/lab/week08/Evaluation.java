package lab.week08;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.*;
import java.util.*;

/** Runs the small supplied case list; semantic judgment remains source-based review. */
public final class Evaluation {
    private static String answerReview(Map<String, Object> result) {
        boolean received = Set.of("ANSWERED", "ABSTAINED").contains(Objects.toString(result.get("status"), ""));
        return received && Boolean.TRUE.equals(result.get("model_called"))
                ? "COMPARE_WITH_SOURCE_AND_CRITERION" : "NOT_VERIFIED";
    }
    @FunctionalInterface public interface Runner { Map<String, Object> run(String query) throws Exception; }
    @FunctionalInterface public interface EvidenceRunner {
        Map<String, Object> run(String query, Map<String, String> sources) throws Exception;
    }

    /** Runs the application's generation and acceptance boundary with supplied evidence. */
    public static void evaluateEvidence(Path casesFile, EvidenceRunner runner) throws Exception {
        var cases = Quickstart.JSON.readTree(Files.readString(casesFile));
        for (var item : cases) {
            Map<String, String> sources = Quickstart.JSON.convertValue(item.get("sources"), new TypeReference<Map<String, String>>() {});
            var result = new LinkedHashMap<>(runner.run(item.path("query").asText(), sources));
            result.put("case_id", item.path("id").asText());
            result.put("answer_criterion", item.path("answer_criterion").asText());
            result.put("expected_status", item.path("expected_status").asText());
            result.put("answer_review", answerReview(result));
            System.out.println(Quickstart.json(result));
        }
    }

    /** Reuses the same cases and output format with the learner's application connection. */
    public static void evaluate(Path casesFile, Runner runner) throws Exception {
        var cases = Quickstart.JSON.readTree(Files.readString(casesFile));
        for (var item : cases) {
            var result = new LinkedHashMap<>(runner.run(item.path("query").asText()));
            result.put("case_id", item.path("id").asText());
            result.put("answer_criterion", item.path("answer_criterion").asText());
            result.put("expected_status", item.path("expected_status").asText());
            String expected = item.path("expected_source").asText("");
            var retrieved = (List<?>) result.get("retrieved_document_ids");
            result.put("retrieval_check", expected.isEmpty() ? (retrieved.isEmpty() ? "PASS" : "REVIEW_RELEVANCE")
                    : (retrieved.contains(expected) ? "PASS" : "MISSING_EXPECTED_SOURCE"));
            result.put("answer_review", answerReview(result));
            System.out.println(Quickstart.json(result));
        }
    }
    public static void main(String[] args) throws Exception {
        boolean live = Arrays.asList(args).contains("--live");
        boolean redTeam = Arrays.asList(args).contains("--red-team");
        boolean semantic = Arrays.asList(args).contains("--semantic");
        if (redTeam && !live) throw new IllegalArgumentException("Red Team generation needs --live; offline cases do not test model behavior");
        String model = System.getenv("OPENAI_EMBEDDING_MODEL");
        EmbeddingIndex index = semantic ? EmbeddingIndex.open(Quickstart.KNOWLEDGE_BASE, Path.of(".local/semantic-index.json"),
                "tenant-alpha", model, EmbeddingIndex.live(model)) : null;
        if (!redTeam) {
            evaluate(Path.of("data/golden.json"), query -> index == null
                    ? Quickstart.run(query, Quickstart.KNOWLEDGE_BASE, .08, live ? Quickstart::generateLive : null)
                    : Quickstart.runSemantic(query, index, .6, live ? Quickstart::generateLive : null));
            return;
        }
        evaluateEvidence(Path.of("data/red-team.json"), (query, sources) -> Quickstart.finish(query,
                Map.of("status", "ANSWERED", "source_texts", sources,
                        "retrieved_document_ids", List.copyOf(sources.keySet())), Quickstart::generateLive));
    }
}
