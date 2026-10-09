package lab.inquiry.intake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class IntakeModel {
    @FunctionalInterface
    interface Call {
        ChatCompletion complete(ChatCompletionCreateParams request);
    }

    private record ServiceName(String id, String name) {}
    private static final List<ServiceName> NAMES = List.of(
            new ServiceName("VPN", "VPN"), new ServiceName("SSO", "통합 로그인"), new ServiceName("MAIL", "메일"));
    static final String QUESTION = "어느 서비스의 문제인가요? "
            + NAMES.stream().map(ServiceName::name).collect(Collectors.joining(", ")) + " 중에서 알려 주세요.";
    static final String INSTRUCTIONS = """
            직원의 문의를 services, symptom, errorMessage 세 필드의 접수 결과로 정리하세요.
            직원이 말한 서비스만 services에 적으세요. 말하지 않았으면 빈 배열로 두고, 증상만으로 서비스를 짐작하지 마세요.
            다음 대응표의 이름으로 말했으면 해당 ID로 적고, 표에 없는 서비스는 직원의 표현 그대로 적으세요.
            """ + NAMES.stream().map(name -> name.id() + " — " + name.name()).collect(Collectors.joining("\n")) + "\n" + """
            대응표는 표기를 맞추는 데만 쓰며, 서비스가 실제로 있는지 판단하거나 목록 밖의 서비스를 빼지 마세요.
            이전 접수 결과가 있으면 새 발언을 반영한 전체 결과를 돌려주세요. 서비스를 더하는 발언이면 더하고, 바로잡는 발언이면 바꾸세요.
            새 발언이 말하지 않은 값은 이전 접수 결과의 값을 유지하세요.
            오류 메시지나 오류 코드를 말했으면 errorMessage에, 그 밖의 증상은 symptom에 적으세요.
            symptom이나 errorMessage에 해당하는 값이 없으면 null로 적으세요.
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

    /** 약속과 다른 응답은 값을 만들지 않는다. 호출 실패와는 별개다. */
    static Intake decode(ChatCompletion response) {
        if (response.choices().isEmpty()) return null;
        var choice = response.choices().get(0);
        if (!choice.finishReason().equals(ChatCompletion.Choice.FinishReason.STOP)
                || choice.message().refusal().isPresent() || choice.message().content().isEmpty()) return null;
        try {
            var value = IntakeWire.JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(choice.message().content().get());
            if (value == null || !value.isObject() || value.size() != 3 || !value.path("services").isArray()
                    || !(value.path("symptom").isTextual() || value.path("symptom").isNull())
                    || !(value.path("errorMessage").isTextual() || value.path("errorMessage").isNull())) return null;
            var services = new LinkedHashSet<String>();
            for (var service : value.path("services")) {
                if (!service.isTextual() || service.textValue().isBlank()) return null;
                services.add(service.textValue());
            }
            return new Intake(List.copyOf(services), value.path("symptom").textValue(), value.path("errorMessage").textValue());
        } catch (JsonProcessingException e) { return null; }
    }
}
