package lab.week08;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static lab.week08.Models.*;

public final class Chunking {
    public static List<Document> loadDocuments(Path root) throws IOException {
        Path manifest = root.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) throw new IOException("knowledge_base/manifest.json is required");
        JsonNode entries = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(Files.readString(manifest)).path("documents");
        var documents = new ArrayList<Document>();
        try (var files = Files.list(root)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList()) {
                String name = path.getFileName().toString();
                JsonNode metadata = entries.get(name);
                if (metadata == null || !metadata.isObject())
                    throw new IllegalArgumentException(name + ": trusted ingestion manifest entry is required");
                String status = required(metadata, "status");
                if (!Set.of("current", "archived", "draft").contains(status))
                    throw new IllegalArgumentException(name + ": unsupported status");
                String body = Files.readString(path).replace("\r\n", "\n");
                // Access and trust come from the ingestion manifest, never the document's own header.
                if (body.startsWith("---\n")) {
                    int closing = body.indexOf("\n---\n", 4);
                    if (closing >= 0) body = body.substring(closing + 5).strip();
                }
                documents.add(new Document(required(metadata, "document_id"), body, "knowledge_base/" + name,
                        status, required(metadata, "tenant_id"), metadata.path("trust").asText().equals("trusted"),
                        metadata.path("version").asText("unknown"), nullable(metadata, "policy_family"),
                        nullable(metadata, "conflicts_with"), metadata.path("fixture_only").isBoolean() && metadata.path("fixture_only").booleanValue()));
            }
        }
        return documents;
    }
    static String required(JsonNode metadata, String name) {
        var value = metadata.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException("Invalid manifest field: " + name);
        return value.textValue();
    }
    static String nullable(JsonNode metadata, String name) {
        var value = metadata.get(name);
        return value == null || value.isNull() || value.asText().isEmpty() ? null : value.asText();
    }
    public static List<Chunk> splitDocuments(List<Document> documents) { return splitDocuments(documents, 600, 80); }
    public static List<Chunk> splitDocuments(List<Document> documents, int maxChars, int overlapChars) {
        if (maxChars < 80 || overlapChars < 0 || overlapChars >= maxChars)
            throw new IllegalArgumentException("maxChars >= 80 and 0 <= overlapChars < maxChars are required");
        var chunks = new ArrayList<Chunk>();
        for (Document doc : documents) {
            var pieces = new ArrayList<String>();
            String current = "";
            for (String raw : doc.text().split("\\n\\s*\\n")) {
                String paragraph = raw.strip();
                if (paragraph.isEmpty()) continue;
                String candidate = (current + "\n\n" + paragraph).strip();
                if (!current.isEmpty() && candidate.length() > maxChars) {
                    pieces.add(current);
                    String prefix = overlapChars == 0 ? "" : current.substring(Math.max(0, current.length() - overlapChars));
                    current = (prefix + "\n\n" + paragraph).strip();
                } else current = candidate;
            }
            if (!current.isEmpty()) pieces.add(current);
            for (int index = 0; index < pieces.size(); index++)
                chunks.add(new Chunk(doc.documentId() + "#" + String.format(Locale.ROOT, "%03d", index), doc.documentId(), pieces.get(index),
                        doc.source(), doc.status(), doc.tenantId(), doc.trusted(), doc.version(), doc.policyFamily(), doc.conflictsWith(), doc.fixtureOnly()));
        }
        return chunks;
    }
}
