package lab.week06;

import com.google.genai.types.*;
import java.util.*;

/** Small fixed input examples. This is not a substitute for the live model's intent selection. */
final class GeminiStructuredOffline {
    static GeminiToolLoop.Gateway gateway() {
        return (model, history, config) -> {
            if (config.responseMimeType().isPresent()) {
                var last = history.get(history.size() - 1).parts().orElseThrow().get(0);
                var data = new LinkedHashMap<String, Object>();
                Map<String, Object> facts = last.functionResponse().flatMap(FunctionResponse::response).orElse(Map.of());
                String id = "";
                if (last.functionResponse().isPresent()) {
                    var call = history.get(history.size() - 2).parts().orElseThrow().get(0).functionCall().orElseThrow();
                    id = (String) call.args().orElseThrow().get("customer_id");
                }
                boolean ask = facts.isEmpty() || facts.containsKey("error");
                data.put("customer_id", id);
                data.put("plan", facts.get("plan"));
                data.put("account_status", facts.get("status"));
                data.put("needs_follow_up", ask);
                data.put("answer", ask ? "고객 번호를 확인해 주세요." : "조회한 고객 정보입니다.");
                try { return answer(GeminiQuickstart.JSON.writeValueAsString(data)); }
                catch (Exception error) { throw new IllegalStateException(error); }
            }
            String text = history.get(history.size() - 1).parts().orElseThrow().get(0).text().orElseThrow();
            if ("요금제를 알려주세요".equals(text)) return answer("고객 번호를 알려주세요.");
            String scenario = switch (text) {
                case "C-100", "C-100 고객의 요금제를 알려주세요." -> "normal";
                case "C-404 고객의 요금제를 알려주세요." -> "unknown";
                case "C-100 고객의 계정 상태를 알려주세요." -> "status";
                case "C-100 고객의 요금제와 계정 상태를 알려주세요." -> "both";
                default -> throw new IllegalArgumentException("Unsupported structured offline input");
            };
            return GeminiToolLoop.offline(scenario).generate(model, List.of(history.get(history.size() - 1)), config);
        };
    }

    static GenerateContentResponse answer(String text) {
        return GenerateContentResponse.builder().candidates(Candidate.builder().finishReason("STOP")
                .content(Content.builder().role("model").parts(Part.fromText(text)).build()).build()).build();
    }
}
