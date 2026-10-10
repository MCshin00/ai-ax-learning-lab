package lab.inquiry.intake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lab.inquiry.status.StatusWire;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntakeWire {
    static final ObjectMapper JSON = new ObjectMapper();

    public static Map<String, Object> intake(Intake intake) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("services", intake.services());
        if (intake.symptom() != null) fields.put("symptom", intake.symptom());
        if (intake.errorMessage() != null) fields.put("errorMessage", intake.errorMessage());
        return fields;
    }

    public static Map<String, Object> fields(IntakeSession.Result result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("outcome", result.failure() != null ? "FAILED" : result.intake().services().isEmpty() ? "NEEDS_INPUT" : "READY");
        if (result.conversationId() != null) fields.put("conversationId", result.conversationId());
        if (result.failure() != null) {
            fields.put("code", result.failure().name());
            fields.put("message", result.failure().message);
        } else {
            fields.put("intake", intake(result.intake()));
            if (result.intake().services().isEmpty()) fields.put("question", IntakeModel.QUESTION);
            else fields.put("statuses", result.statuses().stream().map(StatusWire::fields).toList());
        }
        return fields;
    }

    public static String text(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON 변환에 실패했습니다.", e); }
    }
}
