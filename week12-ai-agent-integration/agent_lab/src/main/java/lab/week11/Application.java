package lab.week11;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** IDE에서 시작하는 상담 앱. LangChain4j와 업무 흐름을 한 프로세스에서 실행합니다. */
public final class Application {
    private Application() {}
    public static List<AiPort.Source> policies() throws IOException {
        var result = new ArrayList<AiPort.Source>();
        for (var row : Json.read(Files.readString(Path.of("data/policies.json"))))
            result.add(new AiPort.Source(row.path("source_id").asText(), row.path("title").asText(),
                row.path("text").asText(), null, "full"));
        return result;
    }
    public static Consultation create(AiPort ai) throws IOException {
        return new Consultation(ai, new OrderLookup(Path.of("data/orders.json")), policies());
    }
    public record Response(int code, Object body) {}
    public static Response dispatch(Consultation app, String json) {
        try {
            var payload = Json.read(json);
            if (payload == null || !payload.isObject() || payload.size() != 2
                    || !payload.path("conversation_id").isTextual() || !payload.path("request").isTextual())
                return new Response(400, Map.of("error", "대화 ID와 문의 문자열을 전달하세요."));
            var result = app.reply(payload.get("conversation_id").asText(), payload.get("request").asText());
            return new Response(Set.of("unavailable", "invalid_output").contains(result.status) ? 502 : 200, result);
        } catch (IllegalArgumentException e) {
            return new Response(400, Map.of("error", "JSON 요청과 입력 길이를 확인하세요."));
        }
    }
    public static HttpServer server(Consultation app, int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            Response response;
            String type = "application/json; charset=utf-8";
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestMethod().equals("GET") && path.equals("/")) {
                response = new Response(200, PAGE); type = "text/html; charset=utf-8";
            } else if (exchange.getRequestMethod().equals("POST") && path.equals("/consultations")) {
                byte[] body = exchange.getRequestBody().readNBytes(20001);
                response = body.length > 20000 ? new Response(400, Map.of("error", "요청이 너무 깁니다."))
                    : dispatch(app, new String(body, StandardCharsets.UTF_8));
            } else response = new Response(404, Map.of("error", "없는 경로입니다."));
            byte[] bytes = (type.startsWith("text/html") ? (String) response.body() : Json.write(response.body()))
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(response.code(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        return server;
    }
    public static void main(String[] args) throws Exception {
        boolean live = false; int port = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--live" -> live = true;
                case "--port" -> port = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("--live와 --port 번호를 사용하세요.");
            }
        }
        AiPort ai;
        try { ai = live ? ModelSetup.live(System::getenv, policies()) : ModelSetup.scripted(policies()); }
        catch (RuntimeException e) {
            System.out.println("모델·검색 준비에 실패했습니다. IDE의 연결 설정과 네트워크를 확인하세요."); return;
        }
        var server = server(create(ai), port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println((live ? "LIVE" : "SCRIPTED_DEMO") + " 상담 화면: http://127.0.0.1:" + server.getAddress().getPort());
    }
    private static final String PAGE = """
        <!doctype html><html lang="ko"><meta charset="utf-8"><title>주문 상담</title>
        <h1>주문 상담</h1><p>확인한 사실과 정책 근거를 함께 읽으세요. 응답의 mode에서 실제 모델 연결 여부를 확인합니다.</p>
        <form id="form"><p><label>대화 ID <input id="conversation" value="customer-1" required></label></p>
        <p><label>문의 <textarea id="request" required>A-102 상태가 궁금해요.</textarea></label></p>
        <button>상담하기</button></form><pre id="output"></pre>
        <script>document.getElementById('form').onsubmit=async e=>{e.preventDefault();
        const out=document.getElementById('output');out.textContent='처리 중';
        try{const response=await fetch('/consultations',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({conversation_id:document.getElementById('conversation').value,
        request:document.getElementById('request').value})});out.textContent=JSON.stringify(await response.json(),null,2);}
        catch(e){out.textContent='연결에 실패했습니다.';}};</script></html>
        """;
}
