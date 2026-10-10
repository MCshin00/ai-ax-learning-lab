package lab.inquiry;

import lab.inquiry.intake.IntakeWire;
import java.util.LinkedHashMap;
import java.util.Map;

final class InquiryWire {
    static Map<String, Object> fields(InquirySession.Result result) {
        var fields = IntakeWire.fields(result.intake());
        if (!fields.get("outcome").equals("READY")) return fields;
        fields.put("evidence", result.evidence().stream().map(InquiryWire::evidence).toList());
        if (result.draft() != null) {
            var draft = new LinkedHashMap<String, Object>();
            draft.put("text", result.draft().text());
            draft.put("sources", result.draft().sources());
            fields.put("draft", draft);
        }
        if (!result.notices().isEmpty()) fields.put("notices", result.notices().stream().map(InquiryWire::notice).toList());
        fields.put("modelCalls", result.modelCalls());
        return fields;
    }

    private static Map<String, Object> evidence(InquirySession.Evidence evidence) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("serviceId", evidence.serviceId());
        fields.put("outcome", evidence.search().outcome().name());
        fields.put("queries", evidence.queries());
        fields.put("documents", evidence.search().documents().stream().map(book -> {
            var document = new LinkedHashMap<String, Object>();
            document.put("id", book.id());
            document.put("title", book.title());
            return document;
        }).toList());
        return fields;
    }

    private static Map<String, Object> notice(InquirySession.Notice notice) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("code", notice.code().name());
        if (notice.serviceId() != null) fields.put("serviceId", notice.serviceId());
        fields.put("message", notice.code().message);
        return fields;
    }
}
