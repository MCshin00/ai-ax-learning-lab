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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InquiryModel {
    enum Judgment { APPLIES, CONDITION_UNCONFIRMED, NOT_COVERED }
    record DocumentJudgment(String id, String condition, Judgment judgment, List<String> actions) {}
    record Judgments(List<DocumentJudgment> documents) {}
    record Result<T>(T value, InquirySession.Code failure) {}

    static final String JUDGMENT_INSTRUCTIONS = """
            직원의 문의(intake)와 운영 문서(documents)를 받습니다.
            문서마다 이 문의에 적용되는지 판단해 documents에 문서 하나에 항목 하나씩, 받은 순서대로 적으세요.

            condition
            - 그 문서가 다루는 증상이나 오류만 문서의 표현 그대로 옮기세요. "~하면"으로 끝나는 조건이면 거기까지,
              "~은/는"으로 시작하는 문장이면 그 앞의 말만 옮기세요. 확인하거나 요청할 일은 넣지 마세요.
            - 문서가 대상을 여러 표현으로 열거하면 그 대안들을 빼지 마세요.
            - intake의 말로 바꿔 쓰지 마세요. 문서 안에서 일마다 따로 붙은 조건은 여기에 적지 말고 actions의 문장에 남기세요.
            예: "인쇄가 중간에 멈추면 멈춘 쪽수를 확인한다." → "인쇄가 중간에 멈추면"
                "스캔 파일 전송 지연은 전송 기록과 공지를 함께 확인한다." → "스캔 파일 전송 지연"

            judgment
            - APPLIES: 직원이 말한 증상이나 오류 메시지가 condition에 해당한다는 것이 intake에 드러나 있습니다.
            - CONDITION_UNCONFIRMED: 직원이 말한 것이 condition에 해당할 수도 있지만, 해당하는지는 직원에게 더 물어야 압니다.
            - NOT_COVERED: 직원이 말한 증상이나 오류는 condition과 다른 종류입니다. 오류 코드가 다른 것도 여기에 해당합니다.
            - APPLIES인지는 intake의 증상과 오류 메시지를 함께 보고 판단하세요.
              기능을 사용할 수 없다는 말만으로 문서가 다루는 특정 증상이 확인된 것은 아닙니다.
              다만 함께 접수된 오류 메시지 등으로 문서의 조건이 확인되면 APPLIES입니다.
            - 해당할 가능성은 있지만 구분할 정보가 부족하면 CONDITION_UNCONFIRMED입니다.
              명시된 증상이나 오류가 문서의 대상과 다르면 NOT_COVERED입니다.
              서비스가 같다는 이유만으로 CONDITION_UNCONFIRMED나 APPLIES로 판단하지 마세요.

            actions
            - judgment가 APPLIES일 때만 적고, 그 밖에는 빈 배열로 두세요.
            - 문서가 확인하거나 요청하라고 한 일과 금지한 일을 문서의 문장마다 한 항목씩 "~합니다"로 끝나게 적으세요.
              운영팀이 읽는 작업 요청에 들어갈 문장입니다. 직원에게 말하는 문장을 쓰지 마세요.
            - 문장 안의 조건("~하면")과 의무·금지의 세기를 바꾸지 마세요. 조건이 붙은 일은 조건을 붙인 채로 적고,
              그 조건이 지금 사실이라고 쓰지 마세요.
            - 문서에 없는 일, 처리 완료, 원인을 더하지 마세요.

            예 (문서: "인쇄가 중간에 멈추면 멈춘 쪽수와 프린터 표시등을 확인한다. 다시 인쇄해도 멈추면 운영팀에 점검을 요청한다. 프린터 덮개를 직접 열지 않는다.")
            - 증상 "인쇄하다가 자꾸 멈춤" → condition "인쇄가 중간에 멈추면", APPLIES,
              actions ["멈춘 쪽수와 프린터 표시등을 확인합니다.", "다시 인쇄해도 멈추면 운영팀에 점검을 요청합니다.", "프린터 덮개를 직접 열지 않습니다."]
            - 증상 "인쇄가 안 됨" → CONDITION_UNCONFIRMED (중간에 멈추는 것인지 알 수 없음), actions []
            - 증상 "인쇄물이 흐림" → NOT_COVERED, actions []

            예 (문서: "스캔 파일 전송 지연은 전송 기록과 공지를 함께 확인한다. 지연이 계속되면 운영팀에 전달한다.")
            - 증상 "스캔 전송이 오래 걸림" → condition "스캔 파일 전송 지연", APPLIES
            - 증상 "스캔이 안 됨" → CONDITION_UNCONFIRMED (지연인지 알 수 없음), actions []
            """;

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String model;
    private final IntakeModel.Call call;

    InquiryModel(String model, IntakeModel.Call call) { this.model = model; this.call = call; }

    Result<Judgments> judge(Intake intake, List<RunbookSearch.Runbook> documents) {
        var input = new LinkedHashMap<String, Object>();
        input.put("intake", IntakeWire.intake(intake));
        input.put("documents", documents);
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", Map.of("type", "string"));
        fields.put("condition", Map.of("type", "string"));
        fields.put("judgment", Map.of("type", "string", "enum",
                List.of("APPLIES", "CONDITION_UNCONFIRMED", "NOT_COVERED")));
        fields.put("actions", Map.of("type", "array", "items", Map.of("type", "string")));
        var item = Map.of("type", "object", "properties", fields,
                "required", List.copyOf(fields.keySet()), "additionalProperties", false);
        return invoke("judgment", JUDGMENT_INSTRUCTIONS, input,
                Map.of("documents", Map.of("type", "array", "items", item)), List.of("documents"), Judgments.class);
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
