package lab.week08;

import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import java.io.IOException;
import java.util.*;
import static lab.week08.Models.*;

/** Lucene의 BM25와 한국어 분석기를 사용하는 작은 메모리 색인입니다. */
public final class Bm25Index implements AutoCloseable {
    private final ByteBuffersDirectory directory = new ByteBuffersDirectory();
    private final KoreanAnalyzer analyzer = new KoreanAnalyzer();
    private final Map<String, Chunk> chunks = new LinkedHashMap<>();
    private final DirectoryReader reader;
    private final IndexSearcher searcher;

    public Bm25Index(List<Chunk> selected, String tenant) throws IOException {
        for (var chunk : selected) {
            if (!Retrieval.eligible(chunk, tenant, false, false)) throw new IllegalArgumentException("검색 범위 밖 자료입니다.");
            if (chunks.putIfAbsent(chunk.chunkId(), chunk) != null) throw new IllegalArgumentException("중복 chunk_id입니다.");
        }
        try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity()))) {
            for (var chunk : selected) {
                var document = new org.apache.lucene.document.Document();
                document.add(new StringField("chunk_id", chunk.chunkId(), Field.Store.YES));
                document.add(new TextField("body", chunk.text(), Field.Store.NO));
                writer.addDocument(document);
            }
        }
        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
    }
    public List<RetrievedChunk> retrieve(String query, int topK) throws IOException {
        if (topK < 1) throw new IllegalArgumentException("후보 수는 양수여야 합니다.");
        var terms = new LinkedHashSet<String>();
        try (var stream = analyzer.tokenStream("body", query)) {
            var term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
                if (terms.size() > 128) throw new IllegalArgumentException("질문을 짧게 나눠 주세요.");
            }
            stream.end();
        }
        if (terms.isEmpty()) return List.of();
        var builder = new BooleanQuery.Builder();
        terms.forEach(term -> builder.add(new TermQuery(new Term("body", term)), BooleanClause.Occur.SHOULD));
        var found = new ArrayList<RetrievedChunk>();
        for (var hit : searcher.search(builder.build(), topK).scoreDocs) {
            var id = searcher.storedFields().document(hit.doc).get("chunk_id");
            found.add(new RetrievedChunk(chunks.get(id), hit.score));
        }
        return found;
    }
    public void close() throws IOException { reader.close(); analyzer.close(); directory.close(); }
}
