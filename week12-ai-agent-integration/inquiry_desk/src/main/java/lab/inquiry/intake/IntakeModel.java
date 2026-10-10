package lab.inquiry.intake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class IntakeModel {
    @FunctionalInterface
    public interface Call {
        ChatCompletion complete(ChatCompletionCreateParams request);
    }

    private record ServiceName(String id, List<String> names) {}
    private static final List<ServiceName> NAMES = List.of(
            new ServiceName("VPN", List.of("VPN")),
            new ServiceName("SSO", List.of("통합 로그인", "통합인증")),
            new ServiceName("MAIL", List.of("메일")));
    static final String QUESTION = "어느 서비스의 문제인가요? "
            + NAMES.stream().map(service -> service.names().get(0)).collect(Collectors.joining(", ")) + " 중에서 알려 주세요.";
    static final String NOT_FOUND_QUESTION = "말씀하신 서비스는 조회 자료에서 찾지 못했습니다. "
            + NAMES.stream().map(service -> service.names().get(0)).collect(Collectors.joining(", "))
            + " 가운데 해당하는 서비스가 있으면 알려 주세요. 다른 시스템이라면 이 도구로는 상태를 확인할 수 없으니 담당자에게 문의해 주세요.";
    static final String INSTRUCTIONS = """
            직원의 문의를 services, symptom, errorMessage 세 필드의 접수 결과로 정리하세요.

            services
            - 직원이 문제가 있다고 말한 사내 서비스와 업무 시스템을 모두 적으세요.
            - 아래 표의 이름으로 말했으면 그 ID로 바꿔 적으세요.
            """ + NAMES.stream().map(service -> "  " + service.names().stream()
                    .map(name -> "\"" + name + "\"").collect(Collectors.joining(", ")) + " → " + service.id())
                    .collect(Collectors.joining("\n")) + "\n" + """
            - 표에 없는 서비스와 시스템도 빼지 말고 직원의 표현 그대로 적으세요. 그 서비스가 실제로 있는지는 판단하지 마세요.
            - 서비스나 시스템의 이름을 말하지 않았으면 빈 배열로 두세요. 와이파이, 네트워크, PC처럼 일반적인 환경을 가리키는 말이나 증상만으로 서비스를 짐작하지 마세요.

            symptom, errorMessage
            - 화면에 나온 오류 메시지나 오류 코드는 errorMessage에, 그 밖에 겪는 현상은 symptom에 적으세요.
            - 해당하는 값이 없으면 null로 적으세요.

            이전 접수 결과(previousIntake)가 있을 때
            - 새 발언을 반영한 전체 결과를 돌려주세요.
            - 서비스를 더하는 발언이면 더하고, "A가 아니라 B"처럼 바로잡는 발언이면 바꾸세요. 이때도 위 표로 ID를 맞추세요.
            - 새 발언이 말하지 않은 필드는 이전 값을 그대로 두세요. "문제예요"처럼 대상만 알리는 말은 증상이 아닙니다.

            예
            {"utterance":"그룹웨어가 안 열려요"} → {"services":["그룹웨어"],"symptom":"안 열림","errorMessage":null}
            {"utterance":"메일이랑 전자결재가 느려요"} → {"services":["MAIL","전자결재"],"symptom":"느림","errorMessage":null}
            {"utterance":"와이파이가 자꾸 끊겨요"} → {"services":[],"symptom":"와이파이가 자꾸 끊김","errorMessage":null}
            {"previousIntake":{"services":["MAIL"],"symptom":"첨부 파일이 안 열림"},"utterance":"메일이 아니라 통합인증 문제예요"} → {"services":["SSO"],"symptom":"첨부 파일이 안 열림","errorMessage":null}
            {"previousIntake":{"services":["VPN"],"symptom":"접속이 안 됨"},"utterance":"0x80 오류 코드가 떠요"} → {"services":["VPN"],"symptom":"접속이 안 됨","errorMessage":"0x80"}
            """;

    static ChatCompletionCreateParams request(String model, Intake previous, String utterance) {
        var schema = ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "services", Map.of("type", "array", "items", Map.of("type", "string")),
                        "symptom", Map.of("type", List.of("string", "null")),
                        "errorMessage", Map.of("type", List.of("string", "null")))))
                .putAdditionalProperty("required", JsonValue.from(List.of("services", "symptom", "errorMessage")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        Map<String, Object> input = new LinkedHashMap<>();
        if (previous != null) input.put("previousIntake", IntakeWire.intake(previous));
        input.put("utterance", utterance);
        return ChatCompletionCreateParams.builder().model(model).addSystemMessage(INSTRUCTIONS)
                .addUserMessage(IntakeWire.text(input))
                .responseFormat(ResponseFormatJsonSchema.builder().jsonSchema(
                        ResponseFormatJsonSchema.JsonSchema.builder().name("intake").strict(true).schema(schema).build()).build())
                .build();
    }

    static Intake decode(ChatCompletion response) {
        if (response.choices().isEmpty()) return null;
        var choice = response.choices().get(0);
        if (!choice.finishReason().equals(ChatCompletion.Choice.FinishReason.STOP)
                || choice.message().refusal().isPresent() || choice.message().content().isEmpty()) return null;
        try {
            var value = IntakeWire.JSON.readTree(choice.message().content().get());
            var services = new LinkedHashSet<String>();
            for (var service : value.path("services")) services.add(service.asText());
            return new Intake(List.copyOf(services), value.path("symptom").textValue(), value.path("errorMessage").textValue());
        } catch (JsonProcessingException e) { return null; }
    }
}
