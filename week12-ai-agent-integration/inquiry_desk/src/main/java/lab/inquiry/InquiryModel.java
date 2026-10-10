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
            직원이 말한 증상의 뜻을 지키면서 운영 문서에 쓰일 법한 다른 표현으로 바꾸세요.
            없던 증상이나 오류를 더하지 마세요. 화면이 넘어가지 않는 것을 지연으로 바꿀 수는 있어도
            지연을 연결 끊김으로 바꾸지 마세요. 오류 코드는 그대로 두세요.
            서비스 이름과 서비스 ID는 검색어에 넣지 마세요.

            예
            {"intake":{"services":["SSO"],"symptom":"인증 후 화면이 넘어가지 않음"},"targetServices":["SSO"]}
            → {"query":"인증 후 화면 전환 지연"}
            {"intake":{"services":["MAIL"],"symptom":"첨부 파일 업로드가 안 됨","errorMessage":"UPLOAD-403"},"targetServices":["MAIL"]}
            → {"query":"첨부 파일 업로드 실패 UPLOAD-403"}
            """;
    static final String DRAFT_INSTRUCTIONS = """
            운영팀이 읽는 작업 요청 초안을 text에 작성하세요. 담당자가 검토하고 고쳐서 보냅니다.
            직원에게 말하는 문장을 쓰지 마세요.
            아래 네 제목을 이 순서로 쓰고, 각 구획은 "~합니다"로 끝나는 짧은 항목으로 작성하세요.
            해당하는 내용이 없으면 "없습니다"라고 쓰고 구획을 채우려고 새 확인 과제를 만들지 마세요.
            1. 접수 내용 — 직원이 말한 서비스, 증상, 오류를 직원이 말한 것임이 드러나게 쓰세요.
            2. 상태 조회로 확인된 것 — 조회가 실제로 돌려준 상태를 쓰세요. 조회하지 못한 것을 정상으로 쓰지 마세요.
            3. 운영 문서 기준으로 확인하거나 요청할 일 — 적용 조건을 채운 문서의 확인·요청 사항을 쓰세요.
            4. 확인되지 않은 것 — 접수되지 않은 사실, 문서의 적용 조건이 확인되지 않은 것, 대응의 근거가 부족한 것을 쓰세요.
            상태 조회로 확인된 사실과 운영 문서가 확인하거나 요청하라고 한 일을 구분하세요.
            문서에 없는 처리 완료나 원인을 쓰지 마세요.
            공통 장애가 없다는 상태를 개인 환경이 정상이라는 뜻으로 쓰지 마세요.
            상태 조회가 실패했거나 자료에 없는 서비스에 대해 문서의 조건문을 현재 사실로 쓰지 마세요.
            예를 들어 "공통 장애가 진행 중이면"은 조건이며, 조회로 확인하지 못했다면 진행 중이라고 쓰면 안 됩니다.
            넘겨받은 문서가 증상과 맞지 않으면 맞지 않는다고 쓰고 그 내용을 대응으로 옮기지 마세요.
            문서의 적용 조건과 의무·금지의 세기를 바꾸지 마세요. 문서에 없는 "필요 시" 같은 조건을 붙이지 말고,
            금지 규칙을 보내는 사람의 약속으로 바꾸지 마세요.
            적용 조건이 확인되지 않았으면 그 대응을 적용하지 말고 확인되지 않았다고 적으세요.
            sources에는 초안의 안내나 문서가 다루는 범위에 대한 설명을 실제로 뒷받침한 문서의 ID를 적으세요.
            어떤 문서의 대응이 이 문의에 적용되지 않는다고 설명할 때 그 문서를 근거로 썼다면 넣으세요.
            쓰지 않은 문서는 넣지 마세요.
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
