package lab.week08;

import java.util.*;
import static lab.week08.Models.*;

/** Preserves each heading with its following text; long sections are not truncated. */
public final class SectionChunking {
    private SectionChunking() {}

    public static List<Chunk> split(List<Document> documents, String tenant) {
        var chunks = new ArrayList<Chunk>();
        var ids = new HashSet<String>();
        for (var doc : documents) {
            if (!doc.status().equals("current")) continue;
            int position = 0;
            for (String part : doc.text().replace("\r\n", "\n").split("(?m)(?=^#{1,6} )")) {
                String text = part.strip();
                if (text.isEmpty()) continue;
                var chunk = new Chunk(doc.documentId() + "#section-" + String.format(Locale.ROOT, "%03d", position++),
                        doc.documentId(), text, doc.source(), doc.status(), doc.tenantId(), doc.trusted(),
                        doc.version(), doc.policyFamily(), doc.conflictsWith(), doc.fixtureOnly());
                if (!Retrieval.eligible(chunk, tenant, false, false)) continue;
                if (!ids.add(chunk.chunkId())) throw new IllegalArgumentException("Duplicate section ID");
                chunks.add(chunk);
            }
        }
        return List.copyOf(chunks);
    }
}
