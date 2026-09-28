package lab.week11;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/** 실제 연결 설정은 IDE에서 전달합니다. 파일 로더는 사용하지 않습니다. */
public final class ModelSetup {
    private ModelSetup() {}
    public static LangChainAi scripted(List<AiPort.Source> policies) {
        var search = new PolicySearch(ScriptedModels.embeddings(), policies);
        return new LangChainAi(ScriptedModels::model, search::search, policies, "SCRIPTED_DEMO");
    }
    public static LangChainAi live(Function<String, String> settings, List<AiPort.Source> policies) {
        var config = configuration(settings);
        var model = OpenAiChatModel.builder().apiKey(config.key()).modelName(config.chatModel())
            .timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        var embeddings = OpenAiEmbeddingModel.builder().apiKey(config.key()).modelName(config.embeddingModel())
            .timeout(Duration.ofSeconds(30)).maxRetries(0).build();
        var search = new PolicySearch(embeddings, policies);
        return new LangChainAi((operation, payload) -> model, search::search, policies, "LIVE");
    }
    // 설정 객체는 키를 포함하므로 자동 toString을 생성하지 않습니다.
    static final class Config {
        private final String key, chatModel, embeddingModel;
        Config(String key, String chatModel, String embeddingModel) {
            this.key = key; this.chatModel = chatModel; this.embeddingModel = embeddingModel;
        }
        String key() { return key; }
        String chatModel() { return chatModel; }
        String embeddingModel() { return embeddingModel; }
    }
    static Config configuration(Function<String, String> settings) {
        if (!"1".equals(settings.apply("AI_AX_LIVE"))) throw new IllegalArgumentException("실제 실행에는 AI_AX_LIVE=1이 필요합니다.");
        String key = settings.apply("OPENAI_API_KEY"), chat = settings.apply("OPENAI_MODEL"),
            embedding = settings.apply("OPENAI_EMBEDDING_MODEL");
        if (key == null || key.isBlank() || chat == null || chat.isBlank() || embedding == null || embedding.isBlank())
            throw new IllegalArgumentException("IDE에서 API 키·대화 모델·임베딩 모델을 지정하세요.");
        return new Config(key, chat, embedding);
    }
}
