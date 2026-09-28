package lab.desk;

import java.util.*;
import static lab.desk.Models.*;

/** 접수 → 사실 조회 → 근거와 초안. 저장 기능은 이 처리 경로에 없습니다. */
public final class IncidentFlow {
    private final Operations operations;private final ModelWork ai;private final boolean agent;
    private final Map<String,Intake> sessions=new HashMap<>();
    public IncidentFlow(Operations operations,ModelWork ai,boolean agent) {
        this.operations=operations;this.ai=ai;this.agent=agent;
    }
    public synchronized Analysis analyze(String conversationId,String text) {
        if(conversationId==null||conversationId.isBlank()||text==null||text.isBlank())throw new IllegalArgumentException("대화 ID와 문의가 필요합니다.");
        var budget=new ModelWork.Budget();var trace=new ArrayList<Object>();var items=new ArrayList<Item>();
        try {
            var intake=ai.intake(text,sessions.get(conversationId),budget);
            if(intake.serviceIds()==null||intake.symptom()==null||intake.question()==null
                ||intake.serviceIds().stream().anyMatch(id->id==null||id.isBlank())
                ||new HashSet<>(intake.serviceIds()).size()!=intake.serviceIds().size())
                return result(conversationId,"invalid_output","접수 결과를 확인하세요.",items,trace,budget);
            sessions.put(conversationId,intake);
            if(intake.serviceIds().isEmpty()||!intake.question().isBlank())
                return result(conversationId,"needs_input",intake.question().isBlank()?"어떤 서비스에서 발생했나요?":intake.question(),items,trace,budget);
            var facts=new ArrayList<Lookup>();
            for(String id:intake.serviceIds()) {
                Lookup found;
                try{found=operations.lookup(id);}catch(RuntimeException e){found=new Lookup("unavailable",id,null);}
                facts.add(found);trace.add(Map.of("tool","get_service_status","serviceId",id,"result",found));
                items.add(new Item(id,found.status(),found.service(),List.of(),found.service()==null?"상태를 확인하지 못했습니다.":found.service().detail()));
            }
            if(facts.stream().noneMatch(f->f.status().equals("found")))return result(conversationId,"unresolved","",items,trace,budget);
            var evidence=new LinkedHashMap<String,Source>();
            var plan=ai.plan(intake,facts,agent,budget,evidence,trace);
            if(plan.items()==null)throw new IllegalArgumentException("항목이 없습니다.");
            var proposed=new HashMap<String,Proposal>();
            for(var p:plan.items()) {
                if(p==null||!intake.serviceIds().contains(p.serviceId())||proposed.put(p.serviceId(),p)!=null)
                    throw new IllegalArgumentException("대상별 결과를 확인하세요.");
            }
            for(int i=0;i<items.size();i++) {
                var item=items.get(i);if(item.facts()==null)continue;
                var p=proposed.get(item.serviceId());
                boolean valid=p!=null&&p.answer()!=null&&!p.answer().isBlank()&&p.sourceIds()!=null&&!p.sourceIds().isEmpty();
                var sources=new ArrayList<Source>();
                if(valid)for(String id:p.sourceIds()) {
                    var s=evidence.get(id);
                    if(s==null||!s.serviceId().equals(item.serviceId())){valid=false;break;}
                    sources.add(s);
                }
                items.set(i,new Item(item.serviceId(),valid?"ready":"review",item.facts(),valid?List.copyOf(sources):List.of(),
                    valid?p.answer():"현재 상태는 확인했습니다. 대응 방법의 근거가 부족해 운영팀 확인이 필요합니다."));
            }
            long ready=items.stream().filter(i->i.status().equals("ready")).count();
            return result(conversationId,ready==items.size()?"ready":ready>0?"partial":"review","",items,trace,budget);
        }catch(RuntimeException e) {
            return result(conversationId,ModelWork.limit(e)?"limit_reached":"processing_failed",
                "초안 작성을 마치지 못했습니다. 확인된 상태와 미처리 항목을 함께 확인하세요.",items,trace,budget);
        }
    }
    private Analysis result(String id,String status,String question,List<Item> items,List<Object> trace,ModelWork.Budget budget) {
        return new Analysis(id,status,question,List.copyOf(items),List.copyOf(trace),budget.calls(),ai.mode+"/"+(agent?"agent":"fixed"));
    }
}
