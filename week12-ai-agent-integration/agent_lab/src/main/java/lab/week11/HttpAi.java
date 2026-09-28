package lab.week11;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 두 실행 환경을 연결하는 제공 코드. 업무 규칙은 Consultation에 있습니다. */
public final class HttpAi implements AiPort {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;
    public HttpAi(String baseUrl) { endpoint = URI.create(baseUrl.replaceAll("/$", "") + "/model"); }

    @Override public Reply invoke(String operation, Object payload, int remainingCalls) {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(150))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of(
                "operation", operation, "payload", payload, "remaining_calls", remainingCalls))))
            .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new Unavailable();
            return Json.convert(Json.read(response.body()), Reply.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable();
        } catch (IOException e) { throw new Unavailable(); }
    }
}
