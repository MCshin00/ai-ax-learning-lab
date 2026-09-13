package lab.week08;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationTest {
    @Test void failedGenerationIsNotReportedAsAnAnswerAvailableForReview() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            Evaluation.evaluateEvidence(Path.of("data/red-team.json"), (query, sources) ->
                    Quickstart.finish(query, Map.of("status", "ANSWERED", "source_texts", sources,
                            "retrieved_document_ids", List.copyOf(sources.keySet())), (q, s) -> {
                        throw new IllegalStateException("provider did not return an answer");
                    }));
        } finally { System.setOut(previous); }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("PROVIDER_ERROR"));
        assertTrue(output.contains("NOT_VERIFIED"));
        assertFalse(output.contains("COMPARE_WITH_SOURCE_AND_CRITERION"));
    }
    @Test void redTeamReachesTheApplicationsEvidenceAndAcceptanceBoundary() throws Exception {
        var sourcesSeen = new ArrayList<Map<String, String>>();
        var bytes = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            Evaluation.evaluateEvidence(Path.of("data/red-team.json"), (query, sources) -> {
                sourcesSeen.add(sources);
                return Map.of("status", "ABSTAINED", "model_called", false, "answer", "application-acceptance");
            });
        } finally { System.setOut(previous); }
        assertEquals(2, sourcesSeen.size());
        assertTrue(sourcesSeen.stream().anyMatch(s -> s.values().stream().anyMatch(t -> t.contains("365"))));
        assertEquals(2, bytes.toString(StandardCharsets.UTF_8).split("application-acceptance", -1).length - 1);
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("NOT_VERIFIED"));
    }
    @Test void suppliedCasesRunThroughTheSelectedApplicationRatherThanTheReference() throws Exception {
        var queries = new ArrayList<String>();
        var bytes = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            Evaluation.evaluate(Path.of("data/golden.json"), query -> {
                queries.add(query);
                return Map.of("answer", "selected-application-result", "retrieved_document_ids", List.of(),
                        "status", "ABSTAINED", "model_called", false, "sources", Map.of(), "source_ids", List.of());
            });
        } finally { System.setOut(previous); }
        assertEquals(4, queries.size());
        assertTrue(queries.stream().anyMatch(q -> q.contains("주차비")));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(4, output.split("selected-application-result", -1).length - 1);
        assertTrue(output.contains("MISSING_EXPECTED_SOURCE"));
    }
}
