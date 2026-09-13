package lab.week08;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static lab.week08.Models.*;

/** Small persisted SDK store; the course does not implement a vector database. */
public final class EmbeddingIndex {
    @FunctionalInterface public interface Embedder { List<float[]> embed(List<String> texts); }
    private final List<Chunk> chunks;
    private final InMemoryEmbeddingStore<TextSegment> store;
    private final Embedder embedder;

    private EmbeddingIndex(List<Chunk> chunks, InMemoryEmbeddingStore<TextSegment> store, Embedder embedder) {
        this.chunks = chunks; this.store = store; this.embedder = embedder;
    }

    public static Embedder live(String model) {
        if (!"1".equals(System.getenv("AI_AX_LIVE")) || System.getenv("OPENAI_API_KEY") == null || model == null || model.isBlank())
            throw new IllegalStateException("Set AI_AX_LIVE=1, OPENAI_API_KEY and OPENAI_EMBEDDING_MODEL before embedding calls.");
        return texts -> {
            var client = OpenAIOkHttpClient.builder().fromEnv().timeout(Duration.ofSeconds(20)).maxRetries(0).build();
            try {
                var response = client.embeddings().create(EmbeddingCreateParams.builder().model(model).inputOfArrayOfStrings(texts).build());
                var ordered = response.data().stream().sorted(Comparator.comparingLong(com.openai.models.embeddings.Embedding::index)).toList();
                if (ordered.size() != texts.size()) throw new IllegalStateException("Incomplete embedding response");
                var vectors = new ArrayList<float[]>();
                for (var item : ordered) {
                    float[] vector = new float[item.embedding().size()];
                    for (int i = 0; i < vector.length; i++) vector[i] = item.embedding().get(i);
                    vectors.add(vector);
                }
                return vectors;
            } finally { client.close(); }
        };
    }

    static List<Chunk> currentChunks(Path knowledgeBase, String tenant) throws IOException {
        return Chunking.splitDocuments(Chunking.loadDocuments(knowledgeBase)).stream()
                .filter(c -> Retrieval.eligible(c, tenant, false, false)).toList();
    }

    public static EmbeddingIndex build(Path knowledgeBase, Path file, String tenant, String model, Embedder embedder) throws IOException {
        return build(currentChunks(knowledgeBase, tenant), file, tenant, model, embedder);
    }

    /** Accepts application-selected chunks; the storage SDK does not choose segmentation. */
    public static EmbeddingIndex build(List<Chunk> selectedChunks, Path file, String tenant, String model, Embedder embedder) throws IOException {
        var chunks = List.copyOf(selectedChunks);
        validateChunks(chunks, tenant);
        if (chunks.isEmpty()) throw new IllegalArgumentException("No eligible documents");
        var vectors = embedder.embed(chunks.stream().map(Chunk::text).toList());
        if (vectors.size() != chunks.size()) throw new IllegalArgumentException("Embedding count mismatch");
        var store = new InMemoryEmbeddingStore<TextSegment>();
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            store.add(Embedding.from(vectors.get(i)), TextSegment.from(chunk.text(), Metadata.from("chunk_id", chunk.chunkId())));
        }
        var snapshot = Map.of("model", model, "tenant", tenant, "chunks", chunks, "store", store.serializeToJson());
        if (file.toAbsolutePath().getParent() != null) Files.createDirectories(file.toAbsolutePath().getParent());
        // Rebuild the small snapshot as a whole: replaced and removed chunks cannot accumulate.
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "index-", ".tmp");
        try {
            Files.writeString(temporary, Quickstart.json(snapshot));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return new EmbeddingIndex(chunks, store, embedder);
    }

    public static EmbeddingIndex open(Path knowledgeBase, Path file, String tenant, String model, Embedder embedder) throws IOException {
        return open(currentChunks(knowledgeBase, tenant), file, tenant, model, embedder);
    }

    public static EmbeddingIndex open(List<Chunk> selectedChunks, Path file, String tenant, String model, Embedder embedder) throws IOException {
        validateChunks(selectedChunks, tenant);
        var snapshot = Quickstart.JSON.readTree(Files.readString(file));
        if (!model.equals(snapshot.path("model").asText()) || !tenant.equals(snapshot.path("tenant").asText()))
            throw new IllegalArgumentException("Index model or tenant differs; rebuild the index");
        List<Chunk> chunks = Quickstart.JSON.convertValue(snapshot.get("chunks"), new TypeReference<List<Chunk>>() {});
        if (!chunks.equals(selectedChunks))
            throw new IllegalArgumentException("INDEX_OUTDATED: documents or access metadata changed; rebuild the index");
        return new EmbeddingIndex(chunks, InMemoryEmbeddingStore.fromJson(snapshot.path("store").asText()), embedder);
    }

    private static void validateChunks(List<Chunk> chunks, String tenant) {
        var ids = new HashSet<String>();
        for (var chunk : chunks) {
            if (!Retrieval.eligible(chunk, tenant, false, false))
                throw new IllegalArgumentException("Chunk is outside the index's access scope");
            if (!ids.add(chunk.chunkId())) throw new IllegalArgumentException("Duplicate chunk_id");
        }
    }

    public List<RetrievedChunk> retrieve(String query, int topK, double minScore) {
        var vector = embedder.embed(List.of(query));
        if (vector.size() != 1) throw new IllegalArgumentException("Query embedding count mismatch");
        var byId = new LinkedHashMap<String, Chunk>();
        chunks.forEach(c -> byId.put(c.chunkId(), c));
        var request = EmbeddingSearchRequest.builder().queryEmbedding(Embedding.from(vector.get(0)))
                .maxResults(topK).minScore(minScore).build();
        // The legacy field lexicalScore carries the retrieval score here, not a lexical similarity.
        return store.search(request).matches().stream().map(m -> new RetrievedChunk(
                Objects.requireNonNull(byId.get(m.embedded().metadata().getString("chunk_id"))), m.score())).toList();
    }
}
