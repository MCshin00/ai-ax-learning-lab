package lab.inquiry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static lab.inquiry.ReviewPlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpPlanTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private HttpResponse<String> send(InquiryHttpServer server, String path, String method, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String inquire(InquiryHttpServer server) throws Exception {
        var response = send(server, "/inquiries", "POST", "{\"conversationId\":\"a|b\",\"utterance\":\"VPN이 자꾸 끊겨요\"}");
        assertEquals(200, response.statusCode());
        String line = response.body().lines().filter(value -> value.startsWith("data: {\"outcome\"")).findFirst().orElseThrow();
        return JSON.readTree(line.substring(6)).at("/review/reviewId").textValue();
    }

    @Test void rewriteStagesAndResultArriveInOrder() throws Exception {
        try (var app = new ReviewPlanSupport(directory, ACCESS, "{\"query\":\"끊김 오류\"}", DRAFT);
             var server = new InquiryHttpServer(0, app.session)) {
            var response = send(server, "/inquiries", "POST", "{\"conversationId\":\"a|b\",\"utterance\":\"VPN 접속이 안 돼요\"}");
            assertEquals(200, response.statusCode());
            assertEquals("text/event-stream; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
            String stages = "event: stage\ndata: {\"stage\":\"INTAKE\"}\n\n"
                    + "event: stage\ndata: {\"stage\":\"STATUS\"}\n\n"
                    + "event: stage\ndata: {\"stage\":\"SEARCH\"}\n\n"
                    + "event: stage\ndata: {\"stage\":\"REWRITE\"}\n\n"
                    + "event: stage\ndata: {\"stage\":\"DRAFT\"}\n\n";
            assertTrue(response.body().startsWith(stages + "event: result\ndata: "), response.body());
            String result = response.body().substring(stages.length() + "event: result\ndata: ".length()).stripTrailing();
            assertEquals("{\"outcome\":\"READY\",\"conversationId\":\"a|b\",\"intake\":{\"services\":[\"VPN\"],\"symptom\":\"접속이 안 됨\"},"
                    + "\"statuses\":[{\"outcome\":\"FOUND\",\"serviceId\":\"VPN\",\"state\":\"normal\",\"detail\":\"현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.\",\"revision\":1}],"
                    + "\"evidence\":[{\"serviceId\":\"VPN\",\"outcome\":\"FOUND\",\"queries\":[\"접속이 안 됨\",\"끊김 오류\"],\"documents\":[{\"id\":\"RB-VPN\",\"title\":\"VPN 연결 오류 접수\"}]}],"
                    + "\"draft\":{\"text\":\"초안 본문\",\"sources\":[\"RB-VPN\"]},\"modelCalls\":3,"
                    + "\"review\":{\"reviewId\":\"7b1c0c1e-0000-4000-8000-000000000001\",\"services\":[{\"serviceId\":\"VPN\",\"revision\":1}],\"unprocessed\":[]}}", result);
            assertEquals(0, app.saves.size());
        }
    }

    @Test void savesCurrentReviewAndRejectsOldReview() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION, DRAFT, CONNECTION, DRAFT);
             var server = new InquiryHttpServer(0, app.session)) {
            String first = inquire(server);
            var saved = send(server, "/requests", "POST", "{\"reviewId\":\"" + first + "\",\"text\":\"고친 본문\"}");
            assertEquals(200, saved.statusCode());
            assertEquals("application/json", saved.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(SAVED, saved.body());
            inquire(server);
            var stale = send(server, "/requests", "POST", "{\"reviewId\":\"" + first + "\",\"text\":\"고친 본문\"}");
            assertEquals(409, stale.statusCode());
            assertEquals(STALE, stale.body());
            assertEquals(1, app.saves.size());
        }
    }

    @Test void rejectsMalformedBodyWrongMethodAndUnknownPath() throws Exception {
        try (var app = new ReviewPlanSupport(directory);
             var server = new InquiryHttpServer(0, app.session)) {
            var bad = send(server, "/inquiries", "POST", "not json");
            assertEquals(400, bad.statusCode());
            assertEquals("application/json", bad.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("{\"outcome\":\"FAILED\",\"code\":\"INVALID_INPUT\",\"message\":\"본문은 JSON 객체여야 하고 conversationId와 utterance는 공백이 아닌 문자열이어야 합니다.\"}", bad.body());
            var wrongMethod = send(server, "/inquiries", "GET", "");
            assertEquals(405, wrongMethod.statusCode());
            assertEquals("POST", wrongMethod.headers().firstValue("Allow").orElseThrow());
            assertEquals(404, send(server, "/missing", "POST", "").statusCode());
        }
    }

    @Test void disconnectAfterFirstEventStillCallsDraft() throws Exception {
        var release = new CountDownLatch(1);
        var drafted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var app = new ReviewPlanSupport(directory, request -> {
            int index = calls.getAndIncrement();
            if (index == 0) {
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return completion(CONNECTION);
            }
            drafted.countDown();
            return completion(DRAFT);
        }); var server = new InquiryHttpServer(0, app.session)) {
            try (var socket = new Socket("127.0.0.1", server.port())) {
                socket.setSoTimeout(10000);
                socket.setSoLinger(true, 0);
                byte[] body = "{\"conversationId\":\"a\",\"utterance\":\"VPN이 자꾸 끊겨요\"}".getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("POST /inquiries HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().write(body);
                socket.getOutputStream().flush();
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                do { line = reader.readLine(); assertNotNull(line); }
                while (!line.equals("data: {\"stage\":\"INTAKE\"}"));
            } finally { release.countDown(); }
            assertTrue(drafted.await(15, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }
}
