package lab.week08;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import static lab.week08.Models.*;

/** Deterministic lexical baseline. No learned embedding model is used here. */
public final class Retrieval {
    private static final Pattern TOKEN = Pattern.compile("[0-9A-Za-z가-힣]+");
    public static List<String> tokenize(String text) {
        var features = new ArrayList<String>();
        var matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            features.add("word:" + token);
            if (token.matches("[가-힣]+")) for (int width : new int[]{2, 3})
                for (int i = 0; i + width <= token.length(); i++) features.add("char" + width + ":" + token.substring(i, i + width));
        }
        return features;
    }
    static double[] vector(String text) {
        double[] vector = new double[4096];
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String token : tokenize(text)) {
                byte[] digest = hash.digest(token.getBytes(StandardCharsets.UTF_8));
                int index = new BigInteger(1, Arrays.copyOf(digest, 8)).mod(BigInteger.valueOf(vector.length)).intValue();
                vector[index]++;
            }
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        double norm = Math.sqrt(Arrays.stream(vector).map(value -> value * value).sum());
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }
    public static List<RetrievedChunk> retrieve(String query, List<Chunk> chunks, String tenantId, int topK,
                                                 double minScore, boolean includeArchived, boolean includeFixtures) {
        if (topK < 1) throw new IllegalArgumentException("topK must be positive");
        double[] queryVector = vector(query);
        var scored = new ArrayList<RetrievedChunk>();
        for (Chunk chunk : chunks) {
            if (!eligible(chunk, tenantId, includeArchived, includeFixtures)) continue;
            double[] candidate = vector(chunk.text());
            double score = 0;
            for (int i = 0; i < queryVector.length; i++) score += queryVector[i] * candidate[i];
            if (score >= minScore) scored.add(new RetrievedChunk(chunk, Math.rint(score * 1e6) / 1e6));
        }
        return scored.stream().sorted(Comparator.comparingDouble(RetrievedChunk::lexicalScore).reversed()
                .thenComparing(item -> item.chunk().chunkId())).limit(topK).toList();
    }
    public static boolean eligible(Chunk chunk, String tenantId, boolean includeArchived, boolean includeFixtures) {
        return chunk.trusted() && (chunk.tenantId().equals("public") || chunk.tenantId().equals(tenantId))
                && !chunk.status().equals("draft") && (!chunk.fixtureOnly() || includeFixtures)
                && (!chunk.status().equals("archived") || includeArchived);
    }
}
