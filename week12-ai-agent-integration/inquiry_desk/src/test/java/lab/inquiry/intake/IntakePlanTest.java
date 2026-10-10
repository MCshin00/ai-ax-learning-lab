package lab.inquiry.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import lab.inquiry.status.LookupResult;
import lab.inquiry.status.StatusClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class IntakePlanTest {
    private static final String VPN = "{\"outcome\":\"FOUND\",\"serviceId\":\"VPN\",\"state\":\"normal\",\"detail\":\"현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.\",\"revision\":1}";
    private static final String EMPTY = "{\"services\":[],\"symptom\":\"접속이 안 됨\",\"errorMessage\":null}";
    private static final String CERT = "{\"services\":[\"VPN\"],\"symptom\":\"접속이 안 됨\",\"errorMessage\":\"인증서 만료\"}";
    @TempDir Path directory;
    private final List<String> statusCalls = new ArrayList<>();
    private Function<String, LookupResult> statuses = this::lookup;
    private final List<ChatCompletionCreateParams> requests = new ArrayList<>();

    private LookupResult lookup(String serviceId) {
        statusCalls.add(serviceId);
        return Map.of(
                "VPN", LookupResult.found("VPN", "normal", "현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.", 1),
                "SSO", LookupResult.failed("SSO", LookupResult.Cause.DATA_INVALID),
                "급여 시스템", LookupResult.absent("급여 시스템")).get(serviceId);
    }

    private IntakeSession session(String... replies) {
        return new IntakeSession("test-model", request -> {
            int index = requests.size();
            requests.add(request);
            return completion(replies[index]);
        }, statuses);
    }

    private static ChatCompletion completion(String content) {
        return ChatCompletion.builder().id("prepared").created(0).model("test-model")
                .addChoice(ChatCompletion.Choice.builder().index(0).logprobs(java.util.Optional.empty()).finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .message(ChatCompletionMessage.builder().content(content).refusal(java.util.Optional.empty()).build()).build()).build();
    }

    private String line(IntakeSession session, String input) throws Exception {
        var output = new StringWriter();
        IntakeMain.process(new BufferedReader(new StringReader(input + "\n")), new PrintWriter(output), session);
        return output.toString().stripTrailing();
    }

    private JsonNode input(int index) throws Exception {
        return IntakeWire.JSON.readTree(requests.get(index).messages().get(1).asUser().content().asText());
    }

    @Test void a_namedService() throws Exception {
        Files.copy(Path.of("data/services.json"), directory.resolve("services.json"));
        String classpath = System.getProperty("java.class.path");
        System.setProperty("java.class.path", System.getProperty("inquiry.server.classpath"));
        try (var client = new StatusClient(directory)) {
            statuses = client::get;
            var session = session("{\"services\":[\"VPN\"],\"symptom\":\"연결이 자꾸 끊김\",\"errorMessage\":null}");
            var output = new StringWriter();
            assertEquals(0, IntakeMain.process(new BufferedReader(new StringReader("a|VPN이 자꾸 끊겨요\n")), new PrintWriter(output), session));
            assertEquals("{\"outcome\":\"READY\",\"conversationId\":\"a\",\"intake\":{\"services\":[\"VPN\"],\"symptom\":\"연결이 자꾸 끊김\"},\"statuses\":[" + VPN + "]}",
                    output.toString().stripTrailing());
            var request = requests.get(0);
            assertEquals("test-model", request.model().asString());
            assertEquals(2, request.messages().size());
            assertEquals(IntakeModel.INSTRUCTIONS, request.messages().get(0).asSystem().content().asText());
            var format = request.responseFormat().orElseThrow().asJsonSchema().jsonSchema();
            assertEquals(true, format.strict().orElseThrow());
            var schema = format.schema().orElseThrow()._additionalProperties();
            assertEquals(com.openai.core.JsonValue.from(false), schema.get("additionalProperties"));
            assertEquals(com.openai.core.JsonValue.from(List.of("services", "symptom", "errorMessage")), schema.get("required"));
            assertEquals(com.openai.core.JsonValue.from(Map.of(
                    "services", Map.of("type", "array", "items", Map.of("type", "string")),
                    "symptom", Map.of("type", List.of("string", "null")),
                    "errorMessage", Map.of("type", List.of("string", "null")))), schema.get("properties"));
            assertEquals(List.of("VPN이 자꾸 끊겨요"), session.conversation("a").utterances());
        } finally { System.setProperty("java.class.path", classpath); }
    }

    @Test void b_firstUtteranceNeedsServiceWithoutLookup() throws Exception {
        var session = session(EMPTY);
        String output = line(session, "b|접속이 안 돼요");
        assertEquals("{\"outcome\":\"NEEDS_INPUT\",\"conversationId\":\"b\",\"intake\":{\"services\":[],\"symptom\":\"접속이 안 됨\"},\"question\":\"어느 서비스의 문제인가요? VPN, 통합 로그인, 메일 중에서 알려 주세요.\"}", output);
        var result = IntakeWire.JSON.readTree(output);
        assertEquals("NEEDS_INPUT", result.get("outcome").textValue());
        assertEquals(IntakeModel.QUESTION, result.get("question").textValue());
        assertEquals(0, statusCalls.size());
        assertEquals(List.of("접속이 안 돼요"), session.conversation("b").utterances());
    }

    @Test void b_secondUtteranceUsesPreviousIntakeOnly() throws Exception {
        var session = session(EMPTY, CERT);
        line(session, "b|접속이 안 돼요");
        assertEquals(readyB(), line(session, "b|VPN이고 인증서 만료 메시지가 나와요"));
        assertEquals(IntakeWire.JSON.readTree("{\"previousIntake\":{\"services\":[],\"symptom\":\"접속이 안 됨\"},\"utterance\":\"VPN이고 인증서 만료 메시지가 나와요\"}"), input(1));
        assertEquals(List.of("접속이 안 돼요", "VPN이고 인증서 만료 메시지가 나와요"), session.conversation("b").utterances());
    }

    @Test void c_newConversationDoesNotInheritAnotherService() throws Exception {
        var session = session(CERT, "{\"services\":[],\"symptom\":null,\"errorMessage\":\"인증서 만료\"}");
        line(session, "b|VPN이고 인증서 만료 메시지가 나와요");
        var result = IntakeWire.JSON.readTree(line(session, "c|인증서 만료 메시지가 나와요"));
        assertEquals("NEEDS_INPUT", result.get("outcome").textValue());
        assertEquals(IntakeModel.QUESTION, result.get("question").textValue());
        assertEquals("인증서 만료", result.at("/intake/errorMessage").textValue());
        assertFalse(result.get("intake").has("symptom"));
        assertFalse(input(1).has("previousIntake"));
        assertEquals(List.of("VPN"), session.conversation("b").intake().services());
    }

    @Test void e_unknownServiceIsQueried() throws Exception {
        assertEquals("{\"outcome\":\"READY\",\"conversationId\":\"e\",\"intake\":{\"services\":[\"VPN\",\"급여 시스템\"]},\"statuses\":[" + VPN
                        + ",{\"outcome\":\"NOT_FOUND\",\"serviceId\":\"급여 시스템\"}]}",
                line(session("{\"services\":[\"VPN\",\"급여 시스템\"],\"symptom\":null,\"errorMessage\":null}"), "e|VPN이랑 급여 시스템이 안 돼요"));
    }

    @Test void f_badSsoDoesNotEraseVpn() throws Exception {
        var result = IntakeWire.JSON.readTree(line(session("{\"services\":[\"VPN\",\"SSO\"],\"symptom\":null,\"errorMessage\":null}"),
                "f|VPN이 끊기고 통합 로그인도 느려요"));
        assertEquals("READY", result.get("outcome").textValue());
        assertEquals(IntakeWire.JSON.readTree(VPN), result.at("/statuses/0"));
        assertEquals("SSO", result.at("/statuses/1/serviceId").textValue());
        assertEquals("UNAVAILABLE", result.at("/statuses/1/outcome").textValue());
        assertEquals("DATA_INVALID", result.at("/statuses/1/code").textValue());
    }

    @Test void g_duplicateServiceIsQueriedOnce() throws Exception {
        var result = IntakeWire.JSON.readTree(line(session("{\"services\":[\"VPN\",\"VPN\"],\"symptom\":null,\"errorMessage\":null}"), "g|아무 발언"));
        assertEquals("READY", result.get("outcome").textValue());
        assertEquals(IntakeWire.JSON.readTree("[\"VPN\"]"), result.at("/intake/services"));
        assertEquals(1, result.get("statuses").size());
        assertEquals(IntakeWire.JSON.readTree(VPN), result.at("/statuses/0"));
        assertEquals(List.of("VPN"), statusCalls);
    }

    @Test void h_callFailureDoesNotChangeConversation() throws Exception {
        var session = new IntakeSession("test-model", request -> {
            requests.add(request);
            if (requests.size() == 2) throw new OpenAIException("prepared failure");
            return completion(CERT);
        }, statuses);
        line(session, "h|VPN이고 인증서 만료 메시지가 나와요");
        assertEquals("{\"outcome\":\"FAILED\",\"conversationId\":\"h\",\"code\":\"MODEL_UNAVAILABLE\",\"message\":\"모델을 호출하지 못했습니다.\"}", line(session, "h|둘째 발언"));
        line(session, "h|셋째 발언");
        assertEquals(IntakeWire.JSON.readTree(CERT), input(2).get("previousIntake"));
        assertEquals(List.of("VPN이고 인증서 만료 메시지가 나와요", "셋째 발언"), session.conversation("h").utterances());
        assertEquals(3, requests.size());
    }

    @Test void i_incompleteResponseDoesNotChangeConversation() throws Exception {
        var session = new IntakeSession("test-model", request -> {
            requests.add(request);
            var response = completion(CERT);
            return response.toBuilder().choices(List.of(response.choices().get(0).toBuilder()
                    .finishReason(ChatCompletion.Choice.FinishReason.LENGTH).build())).build();
        }, statuses);
        assertEquals("{\"outcome\":\"FAILED\",\"conversationId\":\"i\",\"code\":\"INVALID_OUTPUT\",\"message\":\"모델의 응답이 약속한 형식과 다릅니다.\"}", line(session, "i|아무 발언"));
        assertNull(session.conversation("i"));
        assertEquals(1, requests.size());
    }

    @Test void lineWithoutSeparatorDoesNotCallModel() throws Exception {
        var session = session();
        assertEquals("{\"outcome\":\"FAILED\",\"code\":\"INVALID_INPUT\",\"message\":\"입력은 대화ID|발언 형식이어야 합니다.\"}", line(session, "접속이 안 돼요"));
        assertTrue(requests.isEmpty());
    }

    @Test void missingEnvironmentExitsTwo() throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("inquiry.server.classpath"), IntakeMain.class.getName());
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

    private static String readyB() {
        return "{\"outcome\":\"READY\",\"conversationId\":\"b\",\"intake\":{\"services\":[\"VPN\"],\"symptom\":\"접속이 안 됨\",\"errorMessage\":\"인증서 만료\"},\"statuses\":[" + VPN + "]}";
    }
}
