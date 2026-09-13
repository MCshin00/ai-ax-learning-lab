package lab.week08;

import java.util.List;

public final class Models {
    private Models() {}
    public record Document(String documentId, String text, String source, String status, String tenantId,
                           boolean trusted, String version, String policyFamily, String conflictsWith, boolean fixtureOnly) {}
    public record Chunk(String chunkId, String documentId, String text, String source, String status, String tenantId,
                        boolean trusted, String version, String policyFamily, String conflictsWith, boolean fixtureOnly) {}
    public record RetrievedChunk(Chunk chunk, double lexicalScore) {}
    public record RankedChunk(Chunk chunk, double lexicalScore, double rerankScore, List<String> reasons) {}
}
