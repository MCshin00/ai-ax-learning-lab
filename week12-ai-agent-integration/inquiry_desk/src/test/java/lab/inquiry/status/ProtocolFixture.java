package lab.inquiry.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 무응답을 재현하는 실제 stdio 프로세스. 인증이나 환경 파일을 읽지 않는다. */
public final class ProtocolFixture {
    public static void main(String[] args) throws Exception {
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                JsonNode request = StatusWire.JSON.readTree(line);
                if (!request.has("id")) continue;
                Object result;
                switch (request.path("method").asText()) {
                    case "initialize" -> result = Map.of("protocolVersion", request.path("params").path("protocolVersion").asText(),
                            "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "fixture", "version", "1"));
                    case "tools/list" -> result = Map.of("tools", List.of(StatusWire.tool()));
                    case "tools/call" -> {
                        Thread.sleep(60_000);
                        continue;
                    }
                    default -> throw new AssertionError(request.path("method"));
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("jsonrpc", "2.0"); response.put("id", request.get("id")); response.put("result", result);
                System.out.println(StatusWire.text(response));
                System.out.flush();
            }
        }
    }
}
