package lab.desk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.nio.file.*;
import java.util.*;
import static lab.desk.Models.*;

/** 짧은 운영 문서를 문서 단위로 색인하고 서비스별 후보를 선택합니다. */
public final class EvidenceSearch {
    public record Result(String status,List<Source> sources) {}
    private final EmbeddingModel embeddings;
    private final InMemoryEmbeddingStore<TextSegment> store=new InMemoryEmbeddingStore<>();
    private final Map<String,Source> docs=new LinkedHashMap<>();
    public EvidenceSearch(EmbeddingModel embeddings,List<Source> sources) {
        this.embeddings=embeddings;sources.forEach(s->docs.put(s.id(),s));
        var segments=sources.stream().map(s->TextSegment.from(s.title()+"\n"+s.text(),Metadata.from("id",s.id()))).toList();
        if(!segments.isEmpty())store.addAll(embeddings.embedAll(segments).content(),segments);
    }
    public static List<Source> load(Path file) throws java.io.IOException {
        var result=new ArrayList<Source>();for(var row:Json.read(Files.readString(file)))result.add(Json.as(row,Source.class));return result;
    }
    public Result find(String serviceId,String query) {
        if(query==null||query.isBlank())return new Result("invalid_input",List.of());
        var hits=store.search(EmbeddingSearchRequest.builder().queryEmbedding(embeddings.embed(query).content())
            .maxResults(Math.max(1,docs.size())).minScore(0.65).build()).matches();
        // 제공 자료 전체가 작아서 후보를 모두 받은 뒤 서비스 범위와 상위 2개를 선택합니다.
        var found=hits.stream().map(h->docs.get(h.embedded().metadata().getString("id")))
            .filter(s->s.serviceId().equals(serviceId)).limit(2).toList();
        return new Result(found.isEmpty()?"no_evidence":"found",found);
    }
}
