package lab.desk;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.*;
import static lab.desk.Models.*;

/** 제공 입력의 연결 확인용 대역. 자연어 이해와 실제 검색 품질은 실제 모델로 확인합니다. */
public final class ScriptedModels {
    private ScriptedModels(){}
    public static ChatModel model(String stage,JsonNode input) {
        var responses=new ArrayList<AiMessage>();
        if(stage.equals("intake")) {
            String text=input.path("text").asText();
            var fixtures=Map.of(
                "VPN이 자꾸 끊겨요",new Intake(List.of("VPN"),"VPN 연결 끊김",""),
                "사내망 연결이 자꾸 끊겨요",new Intake(List.of("VPN"),"사내망 연결 끊김",""),
                "접속이 안 돼요",new Intake(List.of(),"접속 불가","어떤 서비스에서 발생했나요?"),
                "VPN이고 인증서 만료 메시지가 나와요",new Intake(List.of("VPN"),"VPN 인증서 만료",""),
                "VPN이 끊기고 통합 로그인도 느려요",new Intake(List.of("VPN","SSO"),"VPN 끊김, SSO 지연",""),
                "급여 시스템이 안 돼요",new Intake(List.of("급여"),"접속 불가",""),
                "VPN이 아니라 통합 로그인 문제예요",new Intake(List.of("SSO"),"SSO 접속 문제",""),
                "VPN과 급여 시스템이 안 돼요",new Intake(List.of("VPN","급여"),"접속 불가",""));
            var intake=fixtures.get(text);
            if(text.equals("인증서 만료 메시지가 나와요")) {
                var ids=new ArrayList<String>();input.path("previous").path("serviceIds").forEach(n->ids.add(n.asText()));
                intake=new Intake(ids,"인증서 만료",ids.isEmpty()?"어떤 서비스에서 발생했나요?":"");
            }
            if(intake==null)throw new IllegalArgumentException("대역 모드에서는 제공 입력을 사용하세요.");
            responses.add(AiMessage.from(Json.write(intake)));
        }else if(stage.equals("rewrite")) {
            // 문서에서 쓰는 서비스 이름과 증상 용어로 바꾼 검색어를 돌려줍니다.
            String symptom=input.path("symptom").asText();
            var queries=new ArrayList<Requery>();
            for(var id:input.path("serviceIds"))queries.add(new Requery(id.asText(),
                id.asText()+(symptom.contains("인증서")?" 인증서 만료":" 연결 오류")));
            responses.add(AiMessage.from(Json.write(new Requeries(queries))));
        }else {
            var proposals=new ArrayList<Proposal>();
            for(var fact:input.path("facts")) {
                if(!fact.path("status").asText().equals("found"))continue;
                String id=fact.path("serviceId").asText();
                String symptom=input.path("intake").path("symptom").asText();
                boolean certificate=id.equals("VPN")&&symptom.contains("인증서");
                if(input.path("agent").asBoolean())responses.add(AiMessage.from(ToolExecutionRequest.builder()
                    .id("search-"+id).name("find_runbook").arguments(Json.write(Map.of("serviceId",id,"query",symptom))).build()));
                proposals.add(new Proposal(id,fact.path("service").path("detail").asText()+
                    (certificate?" 인증서 만료 메시지와 발생 시각을 확인해 갱신 안내를 요청합니다.":" 오류 메시지와 발생 시각을 확인해 운영팀에 전달합니다."),
                    List.of(certificate?"RB-VPN-CERT":"RB-"+id)));
            }
            responses.add(AiMessage.from(Json.write(new Plan(proposals))));
        }
        return sequence(responses);
    }
    public static ChatModel sequence(List<AiMessage> responses) {
        return new ChatModel(){int index;@Override public ChatResponse doChat(ChatRequest request) {
            if(index>=responses.size())throw new IllegalStateException("준비한 응답을 모두 사용했습니다.");
            return ChatResponse.builder().aiMessage(responses.get(index++)).build();
        }};
    }
    public static EmbeddingModel embeddings() {
        return new EmbeddingModel(){@Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream().map(s->{String text=s.text();
                return Embedding.from(new float[]{text.contains("VPN")?1:0,text.contains("SSO")||text.contains("통합 로그인")?1:0,
                    text.contains("MAIL")||text.contains("메일")?1:0,text.contains("인증서 만료")||text.contains("CERT-EXPIRED")?1:0,0.01f});}).toList());
        }};
    }
}
