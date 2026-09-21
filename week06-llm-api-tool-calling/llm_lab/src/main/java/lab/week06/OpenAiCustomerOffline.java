package lab.week06;

import com.openai.models.responses.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed examples for the OpenAI boundary; no credentials or provider calls. */
final class OpenAiCustomerOffline {
    static Quickstart.Gateway gateway() {
        var sequence = new AtomicInteger();
        return request -> {
            int number = sequence.incrementAndGet();
            var input = request.input().orElseThrow().asResponse();
            var last = input.get(input.size() - 1);
            if (request.text().isPresent()) {
                Map<String, Object> facts = Map.of();
                String id = "";
                if (last.isFunctionCallOutput()) {
                    try {
                        facts = OpenAiCustomerAssistant.parseArguments(last.asFunctionCallOutput().output().asString());
                        var call = input.get(input.size() - 2).asFunctionCall();
                        id = (String) OpenAiCustomerAssistant.parseArguments(call.arguments()).get("customer_id");
                    } catch (Exception error) { throw new IllegalArgumentException("Invalid offline history"); }
                }
                var data = new LinkedHashMap<String, Object>();
                boolean missing = facts.isEmpty() || facts.containsKey("error");
                data.put("customer_id", id);
                data.put("plan", facts.get("plan"));
                data.put("account_status", facts.get("status"));
                data.put("needs_follow_up", missing);
                data.put("answer", missing ? "고객 번호를 확인해 주세요." : "조회한 고객 정보입니다.");
                return answer(OpenAiCustomerAssistant.json(data), number);
            }
            String text = Quickstart.JSON.valueToTree(last).path("content").asText();
            if ("요금제를 알려주세요".equals(text)) return answer("고객 번호를 알려주세요.", number);
            String id;
            List<String> fields;
            switch (text) {
                case "C-100", "C-100 고객의 요금제를 알려주세요." -> { id = "C-100"; fields = List.of("plan"); }
                case "C-404 고객의 요금제를 알려주세요." -> { id = "C-404"; fields = List.of("plan"); }
                case "C-100 고객의 계정 상태를 알려주세요." -> { id = "C-100"; fields = List.of("status"); }
                case "C-100 고객의 요금제와 계정 상태를 알려주세요." -> { id = "C-100"; fields = List.of("plan", "status"); }
                default -> throw new IllegalArgumentException("Unsupported offline example input");
            }
            var call = ResponseFunctionToolCall.builder().name(CustomerTool.TOOL_NAME)
                    .callId("offline-" + number).arguments(OpenAiCustomerAssistant.json(Map.of("customer_id", id, "fields", fields))).build();
            return new Quickstart.Turn("completed", "", List.of(ResponseOutputItem.ofFunctionCall(call)), false);
        };
    }

    static Quickstart.Turn answer(String text, int sequence) {
        var message = ResponseOutputMessage.builder().id("offline-message-" + sequence)
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder().text(text).annotations(List.of()).build()).build();
        return new Quickstart.Turn("completed", text, List.of(ResponseOutputItem.ofMessage(message)), false);
    }
}
