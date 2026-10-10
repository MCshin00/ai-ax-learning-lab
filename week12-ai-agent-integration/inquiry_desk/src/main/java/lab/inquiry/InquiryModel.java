package lab.inquiry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lab.inquiry.intake.Intake;
import lab.inquiry.intake.IntakeModel;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.LookupResult;
import lab.inquiry.status.StatusWire;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InquiryModel {
    record Rewrite(String query) {}
    record Draft(String text, List<String> sources) {}
    record Result<T>(T value, InquirySession.Code failure) {}

    static final String REWRITE_INSTRUCTIONS = """
            운영 문서를 검색할 검색어 하나를 query에 적으세요.
            접수 결과 중 targetServices에 해당하는 증상과 오류 코드를 빠뜨리지 말고,
            운영 문서에서 쓸 법한 용어로 바꾸세요. 서비스 이름은 검색어에 넣지 마세요.
            """;
    static final String DRAFT_INSTRUCTIONS = """
            담당자가 검토할 작업 요청 초안을 text에 작성하세요.
            상태 조회로 확인된 사실과 운영 문서가 확인하거나 요청하라고 한 일을 구분하세요.
            문서에 없는 처리 완료나 원인을 쓰지 마세요.
            공통 장애가 없다는 상태를 개인 환경이 정상이라는 뜻으로 쓰지 마세요.
            상태 조회가 실패했거나 자료에 없는 서비스에 대해 문서의 조건문을 현재 사실로 쓰지 마세요.
            예를 들어 "공통 장애가 진행 중이면"은 조건이며, 조회로 확인하지 못했다면 진행 중이라고 쓰면 안 됩니다.
            넘겨받은 문서가 증상과 맞지 않으면 맞지 않는다고 쓰고 그 내용을 대응으로 옮기지 마세요.
            실제로 근거로 쓴 문서의 ID만 sources에 적으세요.
            """;

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String model;
    private final IntakeModel.Call call;

    InquiryModel(String model, IntakeModel.Call call) { this.model = model; this.call = call; }

    Result<Rewrite> rewrite(Intake intake, List<String> services) {
        var input = new LinkedHashMap<String, Object>();
        input.put("intake", IntakeWire.intake(intake));
        input.put("targetServices", services);
        var result = invoke("rewrite", REWRITE_INSTRUCTIONS, input,
                Map.of("query", Map.of("type", "string")), List.of("query"), Rewrite.class);
        if (result.failure() == null && result.value().query().isEmpty()) {
            return new Result<>(null, InquirySession.Code.INVALID_OUTPUT);
        }
        return result;
    }

    Result<Draft> draft(Intake intake, List<LookupResult> statuses, List<RunbookSearch.Runbook> documents) {
        var input = new LinkedHashMap<String, Object>();
        input.put("intake", IntakeWire.intake(intake));
        input.put("statuses", statuses.stream().map(StatusWire::fields).toList());
        input.put("documents", documents);
        return invoke("draft", DRAFT_INSTRUCTIONS, input, Map.of(
                "text", Map.of("type", "string"),
                "sources", Map.of("type", "array", "items", Map.of("type", "string"))),
                List.of("text", "sources"), Draft.class);
    }

    private <T> Result<T> invoke(String name, String instructions, Object input,
                                 Map<String, Object> properties, List<String> required, Class<T> type) {
        var schema = ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(required))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build();
        var request = ChatCompletionCreateParams.builder().model(model).addSystemMessage(instructions)
                .addUserMessage(IntakeWire.text(input))
                .responseFormat(ResponseFormatJsonSchema.builder().jsonSchema(
                        ResponseFormatJsonSchema.JsonSchema.builder().name(name).strict(true).schema(schema).build()).build())
                .build();
        try {
            var response = call.complete(request);
            if (response.choices().isEmpty()) return new Result<>(null, InquirySession.Code.INVALID_OUTPUT);
            var choice = response.choices().get(0);
            if (!choice.finishReason().equals(ChatCompletion.Choice.FinishReason.STOP)
                    || choice.message().refusal().isPresent() || choice.message().content().isEmpty()) {
                return new Result<>(null, InquirySession.Code.INVALID_OUTPUT);
            }
            return new Result<>(JSON.readValue(choice.message().content().get(), type), null);
        } catch (OpenAIInvalidDataException | JsonProcessingException e) {
            return new Result<>(null, InquirySession.Code.INVALID_OUTPUT);
        } catch (OpenAIException e) { return new Result<>(null, InquirySession.Code.MODEL_UNAVAILABLE); }
    }
}
