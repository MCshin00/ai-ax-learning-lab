package lab.week11;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 모델 처리에 필요한 값만 보내며 대화 상태·업무 결과는 Java 앱이 보관합니다. */
public interface AiPort {
    Reply invoke(String operation, Object payload, int remainingCalls);

    record Source(String sourceId, String title, String text, Double score, String scope) {}
    record Reply(String status, JsonNode value, int modelCalls, List<Source> sources,
                 List<JsonNode> trace, String mode) {}

    /** 응답을 받지 못했으므로 실제 모델 시도 횟수를 확정할 수 없습니다. */
    final class Unavailable extends RuntimeException {
        public Unavailable() { super("모델 처리 결과를 받지 못했습니다."); }
    }
}
