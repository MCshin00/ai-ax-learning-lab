import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class PostToolUseReviewTest {
    private static JsonObject object(String raw) { return JsonParser.parseString(raw).getAsJsonObject(); }
    @Test void failedCommandReturnsFeedbackWithoutRawOutput() {
        JsonObject payload = object("{\"tool_name\":\"Bash\",\"tool_response\":{\"exit_code\":1,\"output\":\"PRIVATE_OUTPUT\"}}");
        JsonObject result = PostToolUseReview.hookOutput(payload);
        assertTrue(result.get("systemMessage").getAsString().contains("종료 코드 1"));
        JsonObject feedback = result.getAsJsonObject("hookSpecificOutput");
        assertEquals("PostToolUse", feedback.get("hookEventName").getAsString());
        assertTrue(feedback.get("additionalContext").getAsString().contains("같은 검사를 다시 실행"));
        assertFalse(result.toString().contains("PRIVATE"));
        assertFalse(PostToolUseReview.safeRecord(payload).toString().contains("PRIVATE"));
    }
    @Test void successfulCommandNeedsNoFeedback() {
        for (String raw : new String[]{"{\"tool_response\":{\"exit_code\":0}}", "{\"toolResponse\":{\"exitCode\":0}}"})
            assertEquals(new JsonObject(), PostToolUseReview.hookOutput(object(raw)));
    }
    @Test void missingOrInvalidExitCodeIsNotSuccess() {
        for (String response : new String[]{"{}", "{\"exit_code\":null}", "{\"exit_code\":false}", "{\"exit_code\":\"0\"}", "{\"exit_code\":0.0}", "\"pending\""}) {
            JsonObject result = PostToolUseReview.hookOutput(object("{\"tool_response\":"+response+"}"));
            assertTrue(result.get("systemMessage").getAsString().contains("확인하지 못했습니다"));
            assertTrue(result.getAsJsonObject("hookSpecificOutput").get("additionalContext").getAsString().contains("실행 완료 여부"));
        }
    }
    @Test void recordContainsOnlyMinimalEventFields() {
        JsonObject record = PostToolUseReview.safeRecord(object("{\"toolName\":\"Bash\",\"turnId\":\"turn-1\",\"tool_input\":{\"command\":\"PRIVATE_COMMAND\"},\"toolResponse\":{\"exitCode\":2,\"output\":\"PRIVATE_OUTPUT\"}}"));
        assertEquals(java.util.Set.of("recorded_at", "tool_name", "exit_code", "turn_id"), record.keySet());
        assertEquals(2, record.get("exit_code").getAsInt());
        assertEquals("Bash", record.get("tool_name").getAsString());
        assertEquals("turn-1", record.get("turn_id").getAsString());
        assertFalse(record.toString().contains("PRIVATE"));
    }
    @Test void invalidPayloadReportsErrorWithoutOutput() {
        for (String raw : new String[]{"not-json", "[]", "null", "{} {}", "{cwd:'.'}"}) {
            StringWriter out = new StringWriter(), err = new StringWriter();
            assertEquals(2, PostToolUseReview.run(new StringReader(raw), new PrintWriter(out), new PrintWriter(err)));
            assertTrue(out.toString().isEmpty()); assertFalse(err.toString().isEmpty());
        }
    }
}
