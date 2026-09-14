package lab.week08;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static lab.week08.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class RagApplicationTest {
    @TempDir Path temp;
    static final String BODY = "# 환불 정책\n\n## 접수 기한\n\n이중 결제 환불 접수 기간은 결제일로부터 30일이다.\n\n"
            + "## 확인 절차\n\n소유권이 확인되지 않으면 처리를 중단하고 담당자에게 이관한다.\n\n"
            + "## 승인\n\n환불 실행에는 별도의 사람 승인이 필요하다.";
    static Document document(String id, String text, String status, String tenant, boolean trusted, boolean fixture) {
        return new Document(id, text, "knowledge_base/" + id + ".md", status, tenant, trusted, "2", "refund", null, fixture);
    }
    static List<Document> documents() {
        return List.of(document("refund-policy", BODY, "current", "public", true, false),
                document("private", BODY, "current", "tenant-beta", true, false),
                document("archive", BODY, "archived", "public", true, false),
                document("draft", BODY, "draft", "public", true, false),
                document("fixture", BODY, "current", "public", true, true),
                document("untrusted", BODY, "current", "public", false, false));
    }
    static final Map<String, String> FAKE_SETTINGS = Map.of("AI_AX_LIVE", "1", "OPENAI_API_KEY", "fixture-only",
            "OPENAI_EMBEDDING_MODEL", "fixture-embedding", "OPENAI_MODEL", "fixture-generator");
    static final EmbeddingIndex.Embedder FAKE_EMBEDDER = texts -> texts.stream()
            .map(t -> t.contains("접수 기간") || t.equals("표현이 다른 질문") ? new float[]{1, 0} : new float[]{0, 1}).toList();

    RagApplication.Dependencies dependencies(List<Document> docs, EmbeddingIndex.Embedder embedder, Quickstart.Generator generator) {
        return new RagApplication.Dependencies(path -> docs, FAKE_SETTINGS::get, model -> embedder, generator);
    }
    String run(RagApplication.Dependencies dependencies, String... args) {
        var output = new ByteArrayOutputStream();
        var previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RagApplication.run(args, dependencies);
        } finally { System.setOut(previous); }
        return output.toString(StandardCharsets.UTF_8);
    }
    Map<String, Object> result(RagApplication.Dependencies deps, String... args) throws Exception {
        return Quickstart.JSON.readValue(run(deps, args), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    @Test void sectionsKeepHeadingWithItsBodyAndExcludeIneligibleDocuments() {
        var chunks = SectionChunking.split(documents(), RagApplication.TENANT);
        assertEquals(4, chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.documentId().equals("refund-policy")));
        var confirmation = chunks.stream().filter(c -> c.text().startsWith("## 확인 절차")).findFirst().orElseThrow();
        assertTrue(confirmation.text().contains("중단하고 담당자에게 이관"));
        assertEquals(BODY, ContextChoices.parentDocument(chunks.get(1), chunks).get("refund-policy"));
    }

    @Test void lexicalNeedsNeitherSettingsNorProviderAndExpandsPolicy() throws Exception {
        var deps = new RagApplication.Dependencies(path -> documents(), name -> { fail("Read configuration in lexical run"); return null; },
                model -> { fail("Created embedding provider in lexical run"); return null; },
                (q, sources) -> { fail("Generated during retrieval-only run"); return null; });
        var result = result(deps, "--query", "환불 접수 기간", "--retrieve-only");
        assertEquals("EVIDENCE_READY", result.get("status"));
        assertEquals(BODY, ((Map<?, ?>) result.get("sources")).get("refund-policy"));
        assertEquals(false, result.get("model_called"));
        assertEquals("MARKDOWN_SECTIONS", ((Map<?, ?>) result.get("retrieval_settings")).get("chunking"));
    }

    @Test void indexRoundTripUsesChosenSectionsAndQueryOnlyThenPassesExpandedContext() throws Exception {
        Path file = temp.resolve("sections.json");
        var calls = new ArrayList<List<String>>();
        EmbeddingIndex.Embedder embedder = texts -> { calls.add(List.copyOf(texts)); return FAKE_EMBEDDER.embed(texts); };
        var generated = new ArrayList<Map<String, String>>();
        var deps = dependencies(documents(), embedder, (query, sources) -> {
            assertEquals("표현이 다른 질문", query);
            generated.add(sources);
            assertEquals(BODY, sources.get("refund-policy"));
            return Map.of("status", "ANSWERED", "answer", "소유권 확인 전에는 처리를 중단하고 이관합니다.", "source_ids", List.of("refund-policy"));
        });
        assertEquals("INDEX_READY", result(deps, "--prepare", "--index", file.toString()).get("status"));
        assertEquals(SectionChunking.split(documents(), RagApplication.TENANT).stream().map(Chunk::text).toList(), calls.get(0));
        calls.clear();
        var response = result(deps, "--semantic", "--generate", "--index", file.toString(), "--min-score", "0.99", "--query", "표현이 다른 질문");
        assertEquals(List.of(List.of("표현이 다른 질문")), calls);
        assertEquals(1, ((List<?>) response.get("retrieved_chunks")).size());
        assertEquals(1, generated.size());
        assertEquals("ANSWERED", response.get("status"));
        assertEquals(true, response.get("model_called"));
    }

    @Test void staleMissingAndCorruptSnapshotsDoNotRunRetrieval() throws Exception {
        Path file = temp.resolve("sections.json");
        var deps = dependencies(documents(), FAKE_EMBEDDER, null);
        assertEquals("INDEX_REQUIRED", result(deps, "--semantic", "--index", file.toString()).get("status"));
        result(deps, "--prepare", "--index", file.toString());
        var changed = List.of(document("refund-policy", BODY.replace("30일", "45일"), "current", "public", true, false));
        var noCalls = dependencies(changed, texts -> { fail("Embedded with stale index"); return null; }, null);
        assertEquals("INDEX_OUTDATED", result(noCalls, "--semantic", "--index", file.toString()).get("status"));
        result(dependencies(changed, FAKE_EMBEDDER, null), "--prepare", "--index", file.toString());
        var rebuilt = result(dependencies(changed, FAKE_EMBEDDER, null), "--semantic", "--index", file.toString(), "--query", "표현이 다른 질문");
        assertTrue(((Map<?, ?>) rebuilt.get("sources")).get("refund-policy").toString().contains("45일"));
        Files.writeString(file, "invalid-json");
        assertEquals("INDEX_ERROR", result(deps, "--semantic", "--index", file.toString()).get("status"));
    }

    @Test void noEvidenceStopsGenerationAndProviderFailuresAreDistinct() throws Exception {
        var deps = dependencies(documents(), FAKE_EMBEDDER, (q, s) -> { fail("Called generator without evidence"); return null; });
        var noEvidence = result(deps, "--generate", "--min-score", "1", "--query", "무관한 주차비 질문");
        assertEquals("ABSTAINED", noEvidence.get("status"));
        assertEquals(false, noEvidence.get("model_called"));
        var badGenerator = dependencies(documents(), FAKE_EMBEDDER, (q, s) -> { throw new IllegalStateException("fixture"); });
        var failure = result(badGenerator, "--generate", "--query", "환불 접수 기간");
        assertEquals("PROVIDER_ERROR", failure.get("status"));
        assertEquals(true, failure.get("model_called"));
        Path indexFile = temp.resolve("sections.json");
        result(deps, "--prepare", "--index", indexFile.toString());
        var badEmbedder = dependencies(documents(), texts -> { throw new IllegalStateException("fixture"); }, null);
        var retrieval = result(badEmbedder, "--semantic", "--index", indexFile.toString());
        assertEquals("PROVIDER_ERROR", retrieval.get("status"));
        assertEquals("RETRIEVAL", retrieval.get("failure_stage"));
        assertEquals(false, retrieval.get("model_called"));
    }

    @Test void embeddingFailuresExposeCategoriesWithoutProviderSecrets() throws Exception {
        String hidden = "fixture-private-auth-material";
        for (var entry : Map.of("insufficient_quota", "QUOTA_EXCEEDED",
                "credit_balance_exhausted", "CREDIT_BALANCE_EXHAUSTED",
                "rate_limit_exceeded", "RATE_LIMITED", hidden, "QUOTA_OR_RATE_LIMIT").entrySet()) {
            var failure = com.openai.errors.RateLimitException.builder()
                    .headers(com.openai.core.http.Headers.builder().build())
                    .error(com.openai.models.ErrorObject.builder().code(entry.getKey())
                            .message(hidden).param(hidden).type(hidden).build()).build();
            var deps = dependencies(documents(), texts -> { throw failure; }, null);
            var output = result(deps, "--prepare", "--index", temp.resolve("failed.json").toString());
            assertEquals(entry.getValue(), output.get("failure_reason"));
            assertEquals(429, output.get("http_status"));
            assertEquals("EMBEDDING", output.get("failure_stage"));
            assertFalse(Quickstart.json(output).contains(hidden));
            assertFalse(Files.exists(temp.resolve("failed.json")));
        }
        var unauthorized = com.openai.errors.UnauthorizedException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("invalid_api_key")
                        .message(hidden).param(hidden).type(hidden).build()).build();
        var auth = result(dependencies(documents(), texts -> { throw unauthorized; }, null), "--prepare");
        assertEquals("AUTHENTICATION_FAILED", auth.get("failure_reason"));
        assertEquals(401, auth.get("http_status"));
        assertFalse(Quickstart.json(auth).contains(hidden));
        var network = result(dependencies(documents(), texts -> {
            throw new com.openai.errors.OpenAIIoException(hidden);
        }, null), "--prepare");
        assertEquals("CONNECTION_ERROR", network.get("failure_reason"));
        assertFalse(network.containsKey("http_status"));
        assertFalse(Quickstart.json(network).contains(hidden));
        Path indexFile = temp.resolve("sections.json");
        result(dependencies(documents(), FAKE_EMBEDDER, null), "--prepare", "--index", indexFile.toString());
        var queryFailure = result(dependencies(documents(), texts -> { throw unauthorized; }, null),
                "--semantic", "--index", indexFile.toString());
        assertEquals("RETRIEVAL", queryFailure.get("failure_stage"));
        assertEquals("AUTHENTICATION_FAILED", queryFailure.get("failure_reason"));
    }

    @Test void unsupportedCitationAndConflictingPoliciesAreRejected() throws Exception {
        var deps = dependencies(documents(), FAKE_EMBEDDER, (q, s) ->
                Map.of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.of("outside")));
        assertEquals("INVALID_OUTPUT", result(deps, "--generate", "--query", "환불 접수 기간").get("status"));
        var first = new Document("first", "환불 접수 기간은 30일이다.", "knowledge_base/first.md", "current", "public", true, "1", "refund", "second", false);
        var second = document("second", "환불 접수 기간은 7일이다.", "current", "public", true, false);
        var conflicts = dependencies(List.of(first, second), FAKE_EMBEDDER, (q, s) -> { fail("Generated conflicting policies"); return null; });
        assertEquals("ABSTAINED", result(conflicts, "--generate", "--query", "환불 접수 기간").get("status"));
    }

    @Test void documentLimitIsIndependentOfTheNumberOfSections() throws Exception {
        var docs = List.of(document("a", "# 환불 접수 기간\n\n## 환불\n\n환불 접수 기간 안내", "current", "public", true, false),
                document("b", "환불 접수 기간", "current", "public", true, false),
                document("c", "환불 접수 기간", "current", "public", true, false));
        var response = result(dependencies(docs, FAKE_EMBEDDER, null), "--query", "환불 접수 기간", "--min-score", "0");
        assertEquals(4, ((List<?>) response.get("retrieved_chunks")).size());
        assertEquals(2, ((Map<?, ?>) response.get("sources")).size());
    }

    @Test void invalidInputsDoNotReadFilesOrSettings() throws Exception {
        var deps = new RagApplication.Dependencies(path -> { fail("Read documents for invalid input"); return null; },
                name -> { fail("Read settings for invalid input"); return null; }, null, null);
        for (String[] args : List.of(new String[]{"--min-score", "NaN"}, new String[]{"--query"},
                new String[]{"--semantic", "--lexical"}, new String[]{"--prepare", "--generate"},
                new String[]{"--generate", "--retrieve-only"}, new String[]{"--evaluate", "--query", "x"},
                new String[]{"--red-team"}, new String[]{"--red-team", "--generate", "--semantic"},
                new String[]{"--red-team", "--generate", "--evaluate"},
                new String[]{"--red-team", "--generate", "--min-score", "0.67"}))
            assertEquals("INVALID_INPUT", result(deps, args).get("status"));
    }

    @Test void redTeamPassesExactEvidenceWithoutDocumentsOrEmbeddingsAndKeepsCitationLimitVisible() throws Exception {
        var cases = Quickstart.JSON.readTree(Files.readString(Path.of("data/red-team.json")));
        var calls = new ArrayList<Map<String, String>>();
        var deps = new RagApplication.Dependencies(path -> { fail("Loaded policies for direct evidence"); return null; },
                name -> {
                    assertNotEquals("OPENAI_EMBEDDING_MODEL", name);
                    return FAKE_SETTINGS.get(name);
                }, model -> { fail("Created embedding provider for direct evidence"); return null; },
                (query, sources) -> {
                    int i = calls.size();
                    assertEquals(cases.get(i).path("query").asText(), query);
                    assertEquals(cases.get(i).path("sources"), Quickstart.JSON.valueToTree(sources));
                    calls.add(Map.copyOf(sources));
                    // Deliberately wrong content with a valid citation demonstrates the validator's scope.
                    return i == 0 ? Map.of("status", "ANSWERED", "answer", "환불 기한은 365일입니다.",
                            "source_ids", List.of("refund-policy"))
                            : Map.of("status", "ABSTAINED", "answer", "주차비 근거가 없습니다.", "source_ids", List.of());
                });
        String output = run(deps, "--red-team", "--generate");
        var reader = Quickstart.JSON.readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (var values = reader.<com.fasterxml.jackson.databind.JsonNode>readValues(output)) {
            for (int i = 0; i < cases.size(); i++) {
                assertTrue(values.hasNextValue());
                var value = values.nextValue();
                assertEquals(cases.get(i).path("id"), value.path("case_id"));
                assertEquals(cases.get(i).path("sources"), value.path("sources"));
                assertEquals("DIRECT_EVIDENCE", value.path("retrieval_method").asText());
                assertTrue(value.path("retrieved_document_ids").isEmpty());
                assertFalse(value.has("retrieval_settings"));
                assertTrue(value.path("model_called").asBoolean());
                assertEquals("COMPARE_WITH_SOURCE_AND_CRITERION", value.path("answer_review").asText());
                assertEquals(i == 0 ? "ANSWERED" : "ABSTAINED", value.path("status").asText());
                if (i == 0) assertTrue(value.path("answer").asText().contains("365일"));
                else assertTrue(value.path("source_ids").isEmpty());
            }
            assertFalse(values.hasNextValue());
        }
        assertEquals(2, calls.size());
    }

    @Test void directEvidenceUsesTheApplicationsOutputValidation() {
        var options = RagApplication.Options.parse(new String[]{"--red-team", "--generate"});
        for (var response : List.of(
                Map.<String, Object>of("status", "ANSWERED", "answer", "가상 답변", "source_ids", List.of("outside")),
                Map.<String, Object>of("status", "ANSWERED", "source_ids", List.of("refund-policy")))) {
            var app = new RagApplication(options, List.of(), null, (query, sources) -> response);
            var result = app.answerFromSources("기한은?", Map.of("refund-policy", "기한은 30일이다."));
            assertEquals("INVALID_OUTPUT", result.get("status"));
            assertEquals(true, result.get("model_called"));
        }
    }

    @Test void goldenCasesUseTheNewApplicationAndExposePolicySettings() throws Exception {
        var docs = Chunking.loadDocuments(Quickstart.KNOWLEDGE_BASE);
        var deps = new RagApplication.Dependencies(path -> docs, name -> { fail("Read real configuration"); return null; }, null, null);
        String output = run(deps, "--evaluate", "--lexical");
        var reader = Quickstart.JSON.readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (var values = reader.<com.fasterxml.jackson.databind.JsonNode>readValues(output)) {
            int count = 0;
            while (values.hasNextValue()) {
                var value = values.nextValue();
                assertEquals("MARKDOWN_SECTIONS", value.path("retrieval_settings").path("chunking").asText());
                assertEquals("PARENT_DOCUMENT", value.path("retrieval_settings").path("context").asText());
                assertFalse(value.path("model_called").asBoolean());
                count++;
            }
            assertEquals(4, count);
        }
    }
}
