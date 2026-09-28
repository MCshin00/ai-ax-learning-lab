package lab.desk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static lab.desk.Models.*;

/** 사람이 검토하는 초안과 실제 저장을 나눕니다. 정정된 대화의 오래된 초안은 저장할 수 없습니다. */
public final class ReviewDesk {
    public record Preview(String id,Analysis analysis,String text,boolean canSave){}
    private final IncidentFlow flow;private final Operations operations;
    private final Map<String,Preview> latest=new HashMap<>();
    public ReviewDesk(IncidentFlow flow,Operations operations){this.flow=flow;this.operations=operations;}
    public synchronized Preview analyze(String conversationId,String text) {
        latest.remove(conversationId);
        var analysis=flow.analyze(conversationId,text);
        var ready=analysis.items().stream().filter(i->i.status().equals("ready")).toList();
        String draft=ready.stream().map(i->i.serviceId()+": "+i.answer()).reduce((a,b)->a+"\n"+b).orElse("");
        var preview=new Preview(UUID.randomUUID().toString(),analysis,draft,!ready.isEmpty());
        latest.put(conversationId,preview);return preview;
    }
    public synchronized JsonNode save(String conversationId,String previewId,String requestId,String reviewedText) {
        var preview=latest.get(conversationId);
        if(preview==null||!preview.id().equals(previewId))return Json.tree(Map.of("status","stale_preview"));
        if(!preview.canSave())return Json.tree(Map.of("status","not_ready"));
        if(requestId==null||requestId.isBlank()||reviewedText==null||reviewedText.isBlank())throw new IllegalArgumentException("저장 ID와 검토한 본문이 필요합니다.");
        var ready=preview.analysis().items().stream().filter(i->i.status().equals("ready")).toList();
        var payload=Json.tree(Map.of("requestId",requestId,"text",reviewedText,
            "facts",ready.stream().map(Item::facts).toList(),"sources",ready.stream().flatMap(i->i.sources().stream()).distinct().toList()));
        try{return operations.save(payload);}
        catch(RuntimeException e){return Json.tree(Map.of("status","save_unknown","message","같은 검토 ID·요청 ID·본문으로 재시도하세요. 이미 저장됐다면 같은 저장 ID를 반환합니다."));}
    }
}
