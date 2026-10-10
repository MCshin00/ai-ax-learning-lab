package lab.inquiry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RunbookSearch {
    record Runbook(String id, String serviceId, String title, String text) {}
    enum Outcome { FOUND, NONE, FAILED }
    record Result(Outcome outcome, List<Runbook> documents, boolean hasServiceDocuments) {}

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Path file;

    RunbookSearch(Path directory) { file = directory.resolve("runbooks.json"); }

    Result search(String serviceId, String query) {
        try {
            var all = JSON.readValue(file.toFile(), new TypeReference<List<Runbook>>() {});
            var books = all.stream().filter(book -> book.serviceId().equals(serviceId)).toList();
            if (books.isEmpty()) return new Result(Outcome.NONE, List.of(), false);
            try (var analyzer = new KoreanAnalyzer(); var directory = new ByteBuffersDirectory()) {
                var config = new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity());
                try (var writer = new IndexWriter(directory, config)) {
                    for (var book : books) {
                        var document = new Document();
                        document.add(new TextField("content", book.title() + "\n" + book.text(), Field.Store.NO));
                        writer.addDocument(document);
                    }
                }
                var terms = new BooleanQuery.Builder();
                try (var tokens = analyzer.tokenStream("content", query)) {
                    var term = tokens.addAttribute(CharTermAttribute.class);
                    tokens.reset();
                    while (tokens.incrementToken()) {
                        terms.add(new TermQuery(new Term("content", term.toString())), BooleanClause.Occur.SHOULD);
                    }
                    tokens.end();
                }
                try (var reader = DirectoryReader.open(directory)) {
                    var searcher = new IndexSearcher(reader);
                    searcher.setSimilarity(new BM25Similarity());
                    var found = new ArrayList<Runbook>();
                    for (var hit : searcher.search(terms.build(), 2).scoreDocs) found.add(books.get(hit.doc));
                    return new Result(found.isEmpty() ? Outcome.NONE : Outcome.FOUND, List.copyOf(found), true);
                }
            }
        } catch (IOException e) { return new Result(Outcome.FAILED, List.of(), false); }
    }
}
