package lab.week11;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 포트 0에서 화면 API → 실제 AI Services → 업무 결과를 고정 응답으로 검사합니다. */
class WorkerIntegrationTest {
    @Test void javaApiToLangChain4jAndBack() throws Exception {
        var ai=ModelSetup.scripted(Application.policies());
        var app=Application.create(ai);
        var server=Application.server(app,0);
        server.start();
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/consultations"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"conversation_id\":\"one\",\"request\":\"A-102 상태가 궁금해요.\"}")).build();
            var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());
            var value=Json.read(response.body());
            assertEquals("ready",value.path("status").asText());
            assertEquals("SCRIPTED_DEMO",value.path("mode").asText());
            assertEquals("배송 준비",value.path("items").get(0).path("facts").path("status").asText());
        } finally { server.stop(0); }
        var cancel=app.reply("cancel","A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.");
        assertEquals("ready",cancel.status);assertEquals(1,cancel.revisions);assertEquals(4,cancel.modelCalls);
        assertEquals("full",cancel.items.get(0).sources.get(0).scope());
        assertEquals("needs_input",app.reply("return","반품하고 싶어요").status);
        assertEquals("needs_input",app.reply("return","A-104예요").status);
        assertEquals("ready",app.reply("return","받은 지 이틀이고 사용하지 않았어요").status);
        var full = new Consultation(ai,new OrderLookup(Path.of("data/orders.json")),Application.policies(),6,false,"full");
        assertEquals(3,full.reply("full","A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.").modelCalls);
    }
}
