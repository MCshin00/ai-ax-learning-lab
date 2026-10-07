package lab.desk;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import static lab.desk.Models.*;

public final class ModelSetup {
    private ModelSetup(){}
    public static ModelWork scripted(List<Source> sources) {
        return new ModelWork(ScriptedModels::model,new EvidenceSearch(ScriptedModels.embeddings(),sources),"SCRIPTED");
    }
    /** 설정 공급자를 주입하므로 검사는 가짜 값만 사용합니다. */
    public static ModelWork live(Function<String,String> settings,List<Source> sources) {
        String key=required(settings,"OPENAI_API_KEY"),chat=required(settings,"OPENAI_MODEL"),embedding=required(settings,"OPENAI_EMBEDDING_MODEL");
        var model=OpenAiChatModel.builder().apiKey(key).modelName(chat).timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        var embeddings=OpenAiEmbeddingModel.builder().apiKey(key).modelName(embedding).timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        return new ModelWork((stage,input)->model,new EvidenceSearch(embeddings,sources),"LIVE");
    }
    private static String required(Function<String,String> settings,String name) {
        String value=settings.apply(name);if(value==null||value.isBlank())throw new IllegalArgumentException("IDE 실행 설정에 "+name+"이 필요합니다.");return value;
    }
}
