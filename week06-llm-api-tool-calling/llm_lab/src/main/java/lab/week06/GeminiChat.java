package lab.week06;

import com.google.genai.types.*;
import java.io.*;
import java.util.*;

/** Console lifetime owns the history; GeminiToolLoop owns each input's request budget. */
final class GeminiChat {
    static void run(String model, GeminiToolLoop.Gateway gateway, String mode,
                    BufferedReader input, PrintWriter output) throws IOException {
        var history = new ArrayList<Content>();
        output.println("대화를 시작합니다. 질문을 입력하세요. 종료: /exit");
        while (true) {
            output.print("입력> ");
            output.flush();
            String text = input.readLine();
            if (text == null || "/exit".equals(text.strip())) {
                output.println("대화를 종료합니다.");
                output.flush();
                return;
            }
            if (text.isBlank()) continue;
            var result = GeminiToolLoop.run(text.strip(), model, gateway, history);
            result.put("mode", mode);
            output.println(GeminiQuickstart.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            if (!"MODEL_RESPONSE".equals(result.get("status"))) {
                output.println("대화를 중단합니다: " + result.get("status")
                        + ". 위 결과를 확인하세요. 다시 실행하면 새 대화가 시작됩니다.");
                output.flush();
                return;
            }
        }
    }

    /** Fixed two-input exercise only. It never calls a provider or interprets arbitrary text. */
    static GeminiToolLoop.Gateway offline() {
        return GeminiChat::offlineResponse;
    }

    private static GenerateContentResponse offlineResponse(String model, List<Content> history,
                                                           GenerateContentConfig config) {
        int current = history.size() - 1;
        while (current >= 0) {
            var content = history.get(current);
            if ("user".equals(content.role().orElse(""))
                    && content.parts().orElse(List.of()).stream().anyMatch(part -> part.text().isPresent())) break;
            current--;
        }
        if (current < 0) throw new IllegalArgumentException("Offline exercise needs a user input");
        String text = history.get(current).parts().orElseThrow().get(0).text().orElseThrow();
        if (current == 0 && history.size() == 1 && "요금제를 알려주세요".equals(text)) {
            return GenerateContentResponse.fromJson("""
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
                {"text":"고객 번호를 알려주세요.","thoughtSignature":"b2ZmbGluZQ=="}]}}]}
                """);
        }
        boolean followup = current == 2 && "C-100".equals(text)
                && "요금제를 알려주세요".equals(history.get(0).parts().orElseThrow().get(0).text().orElse(""))
                && "model".equals(history.get(1).role().orElse(""))
                && "고객 번호를 알려주세요.".equals(history.get(1).parts().orElseThrow().get(0).text().orElse(""));
        boolean single = current == 0 && GeminiToolLoop.DEFAULT_TEXT.equals(text);
        if (!followup && !single) throw new IllegalArgumentException("Unsupported offline exercise input");
        return GeminiToolLoop.offline("normal").generate(model, history.subList(current, history.size()), config);
    }
}
