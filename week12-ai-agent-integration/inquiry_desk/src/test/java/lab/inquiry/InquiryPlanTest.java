package lab.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.LookupResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static lab.inquiry.ReviewPlanSupport.*;

class InquiryPlanTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DETAIL = "현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.";
    private static final String VPN = "{\"outcome\":\"FOUND\",\"serviceId\":\"VPN\",\"state\":\"normal\",\"detail\":\"" + DETAIL + "\",\"revision\":1}";
    private static final String CONNECTION = intake(List.of("VPN"), "자꾸 끊김", null);
    private static final String ACCESS = intake(List.of("VPN"), "접속이 안 됨", null);
    private static final String EMPTY = intake(List.of(), "접속이 안 됨", null);
    private static final String VPN_DOCUMENT = "{\"id\":\"RB-VPN\",\"title\":\"VPN 연결 오류 접수\"}";
    private final List<ChatCompletionCreateParams> requests = new ArrayList<>();
    @TempDir Path temp;

    private static String intake(List<String> services, String symptom, String error) {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("services", services);
        value.put("symptom", symptom);
        value.put("errorMessage", error);
        return IntakeWire.text(value);
    }

    private static String draft(String... sources) {
        return IntakeWire.text(Map.of("documents", java.util.Arrays.stream(sources)
                .map(id -> judgment(id, "VPN 연결이 끊기면", "APPLIES", VPN_ACTIONS)).toList()));
    }

    private static ChatCompletion completion(String content) {
        return ChatCompletion.builder().id("prepared").created(0).model("test-model")
                .addChoice(ChatCompletion.Choice.builder().index(0).logprobs(Optional.empty()).finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .message(ChatCompletionMessage.builder().content(content).refusal(Optional.empty()).build()).build()).build();
    }

    private InquirySession session(Path directory, Object... replies) {
        return session(directory, id -> switch (id) {
            case "급여 시스템", "사내망" -> LookupResult.absent(id);
            case "SSO" -> LookupResult.failed(id, LookupResult.Cause.DATA_INVALID);
            default -> LookupResult.found(id, "normal", DETAIL, 1);
        }, replies);
    }

    private InquirySession session(Path directory, Function<String, LookupResult> statuses, Object... replies) {
        int[] next = {0};
        return new InquirySession("test-model", request -> {
            requests.add(request);
            Object reply = replies[next[0]++];
            if (reply instanceof OpenAIException failure) throw failure;
            return reply instanceof ChatCompletion response ? response : completion((String) reply);
        }, statuses, directory);
    }

    private InquirySession session(Object... replies) { return session(Path.of("data"), replies); }

    private String line(InquirySession session, String input) throws Exception {
        var output = new StringWriter();
        assertEquals(0, InquiryMain.process(new BufferedReader(new StringReader(input + "\n")), new PrintWriter(output), session));
        return output.toString().stripTrailing();
    }

    private JsonNode input(int index) throws Exception { return JSON.readTree(user(index)); }
    private String user(int index) { return requests.get(index).messages().get(1).asUser().content().asText(); }

    private static List<String> ids(JsonNode evidence) {
        var ids = new ArrayList<String>();
        evidence.get("documents").forEach(document -> ids.add(document.get("id").textValue()));
        return ids;
    }

    private static String body(String id) throws Exception {
        for (var book : JSON.readTree(Path.of("data", "runbooks.json").toFile())) {
            if (book.get("id").asText().equals(id)) return book.get("text").asText();
        }
        throw new AssertionError(id);
    }

    private static void notices(JsonNode result, String expected) throws Exception {
        assertFalse(result.has("draft"));
        assertEquals(JSON.readTree(expected), result.get("notices"));
        assertFalse(result.get("statuses").isEmpty());
    }

    private void strict(int index, Map<String, Object> properties, List<String> required) {
        var request = requests.get(index);
        assertEquals("test-model", request.model().asString());
        var format = request.responseFormat().orElseThrow().asJsonSchema().jsonSchema();
        assertTrue(format.strict().orElseThrow());
        var schema = format.schema().orElseThrow()._additionalProperties();
        assertEquals(JsonValue.from(false), schema.get("additionalProperties"));
        assertEquals(JsonValue.from(required), schema.get("required"));
        assertEquals(JsonValue.from(properties), schema.get("properties"));
    }

    @Test void f1_found() throws Exception {
        String output = line(session(CONNECTION, draft("RB-VPN")), "a|VPN이 자꾸 끊겨요");
        assertEquals("{\"outcome\":\"READY\",\"conversationId\":\"a\",\"intake\":{\"services\":[\"VPN\"],\"symptom\":\"자꾸 끊김\"},\"statuses\":[" + VPN
                + "],\"evidence\":[{\"serviceId\":\"VPN\",\"outcome\":\"FOUND\",\"queries\":[\"자꾸 끊김\"],\"documents\":[" + VPN_DOCUMENT
                + "]}],\"draft\":{\"text\":" + IntakeWire.text(APPLIED_TEXT) + ",\"sources\":[\"RB-VPN\"]},\"modelCalls\":2}", output);
        assertEquals(2, requests.size());
        assertEquals(List.of("intake", "documents"), fieldNames(input(1)));
        assertEquals(JSON.readTree("{\"id\":\"RB-VPN\",\"serviceId\":\"VPN\",\"title\":\"VPN 연결 오류 접수\",\"text\":"
                + IntakeWire.text(body("RB-VPN")) + "}"), input(1).at("/documents/0"));
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("id", Map.of("type", "string"));
        fields.put("condition", Map.of("type", "string"));
        fields.put("judgment", Map.of("type", "string", "enum", List.of("APPLIES", "CONDITION_UNCONFIRMED", "NOT_COVERED")));
        fields.put("actions", Map.of("type", "array", "items", Map.of("type", "string")));
        strict(1, Map.of("documents", Map.of("type", "array", "items", Map.of("type", "object", "properties", fields,
                "required", List.copyOf(fields.keySet()), "additionalProperties", false))), List.of("documents"));
        var properties = requests.get(1).responseFormat().orElseThrow().asJsonSchema().jsonSchema()
                .schema().orElseThrow()._additionalProperties().get("properties");
        var schema = properties.convert(JsonNode.class);
        assertEquals(List.of("id", "condition", "judgment", "actions"), fieldNames(schema.at("/documents/items/properties")));
    }

    private static List<String> fieldNames(JsonNode value) {
        var names = new ArrayList<String>();
        value.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test void f2_allDocumentsWithUnconfirmedCondition() throws Exception {
        var result = JSON.readTree(line(session(intake(List.of("VPN", "급여 시스템"), "접속이 안 됨", null), UNCONFIRMED),
                "a|VPN과 급여 시스템 접속이 안 돼요"));
        assertEquals("ALL", result.at("/evidence/0/outcome").asText());
        assertEquals(List.of("RB-VPN", "RB-VPN-CERT"), ids(result.at("/evidence/0")));
        assertEquals(JSON.readTree("[\"접속이 안 됨\"]"), result.at("/evidence/0/queries"));
        assertEquals(UNCONFIRMED_TEXT, result.at("/draft/text").asText());
        assertEquals(JSON.readTree("[\"RB-VPN\"]"), result.at("/draft/sources"));
        assertFalse(result.has("notices"));
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
    }

    @Test void f3_allNotCovered() throws Exception {
        var result = JSON.readTree(line(session(ACCESS, NOT_COVERED), "a|VPN 접속이 안 돼요"));
        assertEquals("ALL", result.at("/evidence/0/outcome").asText());
        notices(result, "[{\"code\":\"NO_EVIDENCE\",\"serviceId\":\"VPN\",\"message\":\"이 문의에 쓸 운영 문서를 찾지 못했습니다.\"}]");
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
    }

    @Test void f4_searchFailed() throws Exception {
        var result = JSON.readTree(line(session(temp, CONNECTION), "a|VPN이 자꾸 끊겨요"));
        assertEquals(JSON.readTree("[{\"serviceId\":\"VPN\",\"outcome\":\"FAILED\",\"queries\":[\"자꾸 끊김\"],\"documents\":[]}]"), result.get("evidence"));
        notices(result, "[{\"code\":\"SEARCH_FAILED\",\"serviceId\":\"VPN\",\"message\":\"운영 문서를 읽지 못했습니다.\"}]");
        assertEquals(1, result.get("modelCalls").asInt());
        assertEquals(1, requests.size());
    }

    @Test void f5_missingExtraOrDuplicateDocument() throws Exception {
        var replies = List.of(draft(), draft("RB-VPN", "RB-SSO"), judgments(
                judgment("RB-VPN", "", "APPLIES", List.of()), judgment("RB-VPN", "", "APPLIES", List.of())));
        for (int i = 0; i < replies.size(); i++) {
            String accepted = i == 2 ? intake(List.of("VPN"), "접속이 안 됨", "인증서 만료") : CONNECTION;
            String utterance = i == 2 ? "a|VPN이고 인증서 만료 메시지가 나와요" : "a|VPN이 자꾸 끊겨요";
            var result = JSON.readTree(line(session(accepted, replies.get(i)), utterance));
            notices(result, "[{\"code\":\"SOURCE_MISMATCH\",\"message\":\"판정이 돌려준 문서가 넘겨준 문서와 맞지 않아 초안을 버렸습니다.\"}]");
            assertEquals(i == 2 ? List.of("RB-VPN-CERT", "RB-VPN") : List.of("RB-VPN"), ids(result.at("/evidence/0")));
            assertEquals(2, result.get("modelCalls").asInt());
        }
        assertEquals(6, requests.size());
    }

    @Test void f6_unknownService() throws Exception {
        var result = JSON.readTree(line(session(intake(List.of("VPN", "급여 시스템"), "자꾸 끊김", null), draft("RB-VPN")), "a|VPN이랑 급여 시스템이 안 돼요"));
        assertEquals(JSON.readTree("{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"급여 시스템\"}"), result.at("/statuses/1"));
        assertEquals(1, result.get("evidence").size());
        assertEquals("VPN", result.at("/evidence/0/serviceId").asText());
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertTrue(result.has("draft"));
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
    }

    @Test void f7_statusFailureStillSearches() throws Exception {
        var result = JSON.readTree(line(session(intake(List.of("VPN", "SSO"), "연결이 끊기고 로그인이 느림", null), draft("RB-VPN", "RB-SSO")), "a|VPN이 끊기고 통합 로그인도 느려요"));
        assertEquals("UNAVAILABLE", result.at("/statuses/1/outcome").asText());
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertEquals(List.of("RB-SSO"), ids(result.at("/evidence/1")));
        assertTrue(user(1).contains(body("RB-VPN")));
        assertTrue(user(1).contains(body("RB-SSO")));
        assertTrue(result.has("draft"));
    }

    @Test void f8_draftCallFailure() throws Exception {
        var result = JSON.readTree(line(session(CONNECTION, new OpenAIException("prepared failure")), "a|VPN이 자꾸 끊겨요"));
        notices(result, "[{\"code\":\"MODEL_UNAVAILABLE\",\"message\":\"모델을 호출하지 못했습니다.\"}]");
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
    }

    @Test void f9_onlyOneServiceApplies() throws Exception {
        var result = JSON.readTree(line(session(intake(List.of("VPN", "MAIL"), "자꾸 끊김", null), judgments(
                judgment("RB-VPN", "VPN 연결이 끊기면", "APPLIES", VPN_ACTIONS),
                judgment("RB-MAIL", "", "NOT_COVERED", List.of("본문에 넣으면 안 되는 항목")))), "a|VPN과 메일이 자꾸 끊겨요"));
        assertEquals(JSON.readTree("[\"자꾸 끊김\"]"), result.at("/evidence/0/queries"));
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertEquals(JSON.readTree("[\"자꾸 끊김\"]"), result.at("/evidence/1/queries"));
        assertEquals(List.of("RB-MAIL"), ids(result.at("/evidence/1")));
        assertEquals(JSON.readTree("[{\"code\":\"NO_EVIDENCE\",\"serviceId\":\"MAIL\",\"message\":\"이 문의에 쓸 운영 문서를 찾지 못했습니다.\"}]"), result.get("notices"));
        assertTrue(result.at("/draft/text").asText().endsWith("4. 상태나 문서를 확인하지 못한 서비스\n- MAIL: 이 문의에 쓸 운영 문서를 찾지 못했습니다."));
        assertFalse(result.at("/draft/text").asText().contains("본문에 넣으면 안 되는 항목"));
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
    }

    @Test void f10_judgmentFailureRetainsIntakeAndSearchNotices() throws Exception {
        String accepted = intake(List.of("VPN", "WIKI"), "자꾸 끊김", null);
        var session = session(accepted, new OpenAIException("prepared failure"), EMPTY);
        var result = JSON.readTree(line(session, "a|VPN과 WIKI가 자꾸 끊겨요"));
        notices(result, "[{\"code\":\"NO_EVIDENCE\",\"serviceId\":\"WIKI\",\"message\":\"이 문의에 쓸 운영 문서를 찾지 못했습니다.\"},"
                + "{\"code\":\"MODEL_UNAVAILABLE\",\"message\":\"모델을 호출하지 못했습니다.\"}]");
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertEquals(2, result.get("modelCalls").asInt());
        assertEquals(2, requests.size());
        line(session, "a|다음 발언");
        assertEquals(result.get("intake"), input(2).get("previousIntake"));
    }

    @Test void f11_truncatedDraft() throws Exception {
        var reply = completion(draft("RB-VPN"));
        reply = reply.toBuilder().choices(List.of(reply.choices().get(0).toBuilder().finishReason(ChatCompletion.Choice.FinishReason.LENGTH).build())).build();
        var result = JSON.readTree(line(session(CONNECTION, reply), "a|VPN이 자꾸 끊겨요"));
        notices(result, "[{\"code\":\"INVALID_OUTPUT\",\"message\":\"모델의 응답이 약속한 형식과 다릅니다.\"}]");
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
    }

    @Test void f12_noSymptom() throws Exception {
        var result = JSON.readTree(line(session(intake(List.of("VPN"), null, null)), "a|VPN이요"));
        notices(result, "[{\"code\":\"NO_SYMPTOM\",\"message\":\"증상이나 오류 메시지가 없어 운영 문서를 찾지 않았습니다.\"}]");
        assertEquals(JSON.readTree("[]"), result.get("evidence"));
        assertEquals(1, result.get("modelCalls").asInt());
        assertEquals(1, requests.size());
    }

    @Test void f13_noDocumentsForService() throws Exception {
        var stages = new ArrayList<String>();
        var analyzed = session(intake(List.of("WIKI"), "편집이 안 됨", null))
                .accept("a", "WIKI 편집이 안 돼요", stages::add);
        var result = JSON.readTree(IntakeWire.text(InquiryWire.fields(analyzed)));
        assertEquals(List.of("INTAKE", "STATUS", "SEARCH"), stages);
        assertEquals(JSON.readTree("[{\"serviceId\":\"WIKI\",\"outcome\":\"NONE\",\"queries\":[\"편집이 안 됨\"],\"documents\":[]}]"), result.get("evidence"));
        notices(result, "[{\"code\":\"NO_EVIDENCE\",\"serviceId\":\"WIKI\",\"message\":\"이 문의에 쓸 운영 문서를 찾지 못했습니다.\"}]");
        assertEquals(1, result.get("modelCalls").asInt());
        assertEquals(1, requests.size());
    }

    @Test void f14_questionDoesNotSearch() throws Exception {
        assertEquals("{\"outcome\":\"NEEDS_INPUT\",\"conversationId\":\"a\",\"intake\":{\"services\":[],\"symptom\":\"접속이 안 됨\"},\"question\":\"어느 서비스의 문제인가요? VPN, 통합 로그인, 메일 중에서 알려 주세요.\"}", line(session(EMPTY), "a|접속이 안 돼요"));
        assertEquals(1, requests.size());
    }

    @Test void f15_secondUtteranceSearchesAfresh() throws Exception {
        var session = session(EMPTY, intake(List.of("VPN"), "접속이 안 됨", "인증서 만료"), judgments(
                judgment("RB-VPN", "VPN 연결이 끊기면", "CONDITION_UNCONFIRMED", List.of("쓰지 않을 항목")),
                judgment("RB-VPN-CERT", "", "APPLIES", List.of("  갱신을 요청합니다.  "))));
        line(session, "a|접속이 안 돼요");
        var result = JSON.readTree(line(session, "a|VPN이고 인증서 만료 메시지가 나와요"));
        assertEquals(List.of("RB-VPN-CERT", "RB-VPN"), ids(result.at("/evidence/0")));
        assertTrue(user(2).contains(body("RB-VPN-CERT")));
        assertEquals(JSON.readTree("[\"RB-VPN-CERT\",\"RB-VPN\"]"), result.at("/draft/sources"));
        assertTrue(result.at("/draft/text").asText().contains("- [RB-VPN-CERT]   갱신을 요청합니다.  \n"
                + "- [RB-VPN] 문서가 다루는 경우(\"VPN 연결이 끊기면\")인지 확인합니다."));
        assertFalse(result.at("/draft/text").asText().contains("쓰지 않을 항목"));
        assertTrue(result.has("draft"));
        assertEquals(2, result.get("modelCalls").asInt());
    }
    @Test void f16_allServicesNotFound() throws Exception {
        String output = line(session(intake(List.of("사내망"), "자꾸 끊김", null)), "s9|사내망 연결이 자꾸 끊겨요");
        assertEquals("{\"outcome\":\"NEEDS_INPUT\",\"conversationId\":\"s9\",\"intake\":{\"services\":[\"사내망\"],\"symptom\":\"자꾸 끊김\"},\"statuses\":[{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"사내망\"}],\"question\":\"말씀하신 서비스는 조회 자료에서 찾지 못했습니다. VPN, 통합 로그인, 메일 가운데 해당하는 서비스가 있으면 알려 주세요. 다른 시스템이라면 이 도구로는 상태를 확인할 수 없으니 담당자에게 문의해 주세요.\"}", output);
        assertEquals(1, requests.size());
    }

    @Test void f17_allStatusesUnavailable() throws Exception {
        var session = session(Path.of("data"), id -> LookupResult.failed(id, LookupResult.Cause.DATA_INVALID), CONNECTION, draft("RB-VPN"));
        var result = JSON.readTree(line(session, "a|VPN이 자꾸 끊겨요"));
        assertEquals("READY", result.get("outcome").asText());
        assertEquals(List.of("RB-VPN"), ids(result.at("/evidence/0")));
        assertTrue(result.has("draft"));
        assertFalse(result.has("question"));
        assertTrue(result.at("/draft/text").asText().contains("2. 상태 조회로 확인된 것\n- 없습니다\n\n"));
        assertTrue(result.at("/draft/text").asText().endsWith("4. 상태나 문서를 확인하지 못한 서비스\n- VPN: 상태를 조회하지 못했습니다."));
    }

    @Test void f18_unusableJudgmentValues() throws Exception {
        for (var item : List.of(judgment("RB-VPN", "", "APPLIES", List.of()),
                judgment("RB-VPN", "  ", "CONDITION_UNCONFIRMED", List.of()))) {
            var result = JSON.readTree(line(session(CONNECTION, judgments(item)), "a|VPN이 자꾸 끊겨요"));
            notices(result, "[{\"code\":\"INVALID_OUTPUT\",\"message\":\"모델의 응답이 약속한 형식과 다릅니다.\"}]");
        }
    }

    @Test void missingEnvironmentExitsTwo() throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("inquiry.server.classpath"), InquiryMain.class.getName());
        builder.environment().remove("OPENAI_API_KEY");
        builder.environment().remove("OPENAI_MODEL");
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        var process = builder.start();
        process.getOutputStream().close();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertEquals(2, process.exitValue());
            assertEquals("", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(error.contains("OPENAI_API_KEY"));
            assertTrue(error.contains("OPENAI_MODEL"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

}
