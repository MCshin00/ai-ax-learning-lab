package lab.week06;

import com.google.genai.types.*;
import java.util.*;

/** Gemini request settings; acceptance rules are shared with the OpenAI path. */
final class GeminiStructuredAnswer {
    static final Map<String, Object> SCHEMA = CustomerAnswer.SCHEMA;

    static GenerateContentConfig config(List<Map<String, Object>> results) {
        return GeminiQuickstart.requestConfig().toBuilder()
                .responseMimeType("application/json").responseJsonSchema(SCHEMA)
                .systemInstruction(Content.fromParts(Part.fromText(CustomerAnswer.instructions(results))))
                .build();
    }

    static Map<String, Object> consume(String raw, List<Map<String, Object>> results) {
        return CustomerAnswer.consume(raw, results);
    }

    static Map<String, Object> hold(String reason) {
        return CustomerAnswer.hold(reason);
    }
}
