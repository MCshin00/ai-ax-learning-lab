package lab.desk;

import java.util.*;
import java.util.function.Consumer;
import static lab.desk.Models.*;

/** 접수 → 사실 조회 → 근거와 초안 → 근거가 없으면 한 번 교정 검색. 저장 기능은 이 처리 경로에 없습니다. */
public final class IncidentFlow {
    private final Operations operations;private final ModelWork ai;private final boolean agent;
    private final Map<String,Intake> sessions=new HashMap<>();
    public IncidentFlow(Operations operations,ModelWork ai,boolean agent) {
        this.operations=operations;this.ai=ai;this.agent=agent;
    }
    public Analysis analyze(String conversationId,String text) {return analyze(conversationId,text,event->{});}
    /** events는 처리 단계를 받는 곳입니다. 조회·검색 기록과 같은 내용을 진행 중에 전달합니다. */
    public synchronized Analysis analyze(String conversationId,String text,Consumer<Object> events) {
        if(conversationId==null||conversationId.isBlank()||text==null||text.isBlank())throw new IllegalArgumentException("대화 ID와 문의가 필요합니다.");
        var budget=new ModelWork.Budget();var trace=new ObservedTrace(events);var items=new ArrayList<Item>();
        try {
            var intake=ai.intake(text,sessions.get(conversationId),budget);
            if(intake.serviceIds()==null||intake.symptom()==null||intake.question()==null
                ||intake.serviceIds().stream().anyMatch(id->id==null||id.isBlank())
                ||new HashSet<>(intake.serviceIds()).size()!=intake.serviceIds().size())
                return result(conversationId,"invalid_output","접수 결과를 확인하세요.",items,trace,budget);
            sessions.put(conversationId,intake);
            events.accept(Map.of("step","intake","intake",intake));
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
            events.accept(Map.of("step","draft"));
            var proposed=proposals(ai.plan(intake,facts,agent,budget,evidence,trace),intake.serviceIds());
            for(int i=0;i<items.size();i++)if(items.get(i).facts()!=null)items.set(i,checked(items.get(i),proposed.get(items.get(i).serviceId()),evidence));
            // 근거 자체를 찾지 못한 서비스만 검색어를 바꿔 한 번 더 찾습니다. 근거가 있는데 초안이 맞지 않으면 사람이 검토합니다.
            var retry=items.stream().filter(i->i.facts()!=null&&i.status().equals("review")
                &&evidence.values().stream().noneMatch(s->s.serviceId().equals(i.serviceId()))).map(Item::serviceId).toList();
            if(!retry.isEmpty()&&ai.correct(intake,retry,budget,evidence,trace)) {
                events.accept(Map.of("step","draft","serviceIds",retry));
                var again=proposals(ai.replan(new Intake(retry,intake.symptom(),""),
                    facts.stream().filter(f->retry.contains(f.serviceId())).toList(),budget,evidence,trace),retry);
                for(int i=0;i<items.size();i++)if(retry.contains(items.get(i).serviceId()))
                    items.set(i,checked(items.get(i),again.get(items.get(i).serviceId()),evidence));
            }
            long ready=items.stream().filter(i->i.status().equals("ready")).count();
            return result(conversationId,ready==items.size()?"ready":ready>0?"partial":"review","",items,trace,budget);
        }catch(RuntimeException e) {
            return result(conversationId,ModelWork.limit(e)?"limit_reached":"processing_failed",
                "초안 작성을 마치지 못했습니다. 확인된 상태와 미처리 항목을 함께 확인하세요.",items,trace,budget);
        }
    }
    private static Map<String,Proposal> proposals(Plan plan,List<String> allowed) {
        if(plan.items()==null)throw new IllegalArgumentException("항목이 없습니다.");
        var proposed=new HashMap<String,Proposal>();
        for(var p:plan.items()) {
            if(p==null||!allowed.contains(p.serviceId())||proposed.put(p.serviceId(),p)!=null)
                throw new IllegalArgumentException("대상별 결과를 확인하세요.");
        }
        return proposed;
    }
    /** 출처가 실제 검색 결과에 있고 같은 서비스의 문서일 때만 ready입니다. */
    private static Item checked(Item item,Proposal p,Map<String,Source> evidence) {
        boolean valid=p!=null&&p.answer()!=null&&!p.answer().isBlank()&&p.sourceIds()!=null&&!p.sourceIds().isEmpty();
        var sources=new ArrayList<Source>();
        if(valid)for(String id:p.sourceIds()) {
            var s=evidence.get(id);
            if(s==null||!s.serviceId().equals(item.serviceId())){valid=false;break;}
            sources.add(s);
        }
        return new Item(item.serviceId(),valid?"ready":"review",item.facts(),valid?List.copyOf(sources):List.of(),
            valid?p.answer():"현재 상태는 확인했습니다. 대응 방법의 근거가 부족해 운영팀 확인이 필요합니다.");
    }
    private Analysis result(String id,String status,String question,List<Item> items,List<Object> trace,ModelWork.Budget budget) {
        return new Analysis(id,status,question,List.copyOf(items),List.copyOf(trace),budget.calls(),ai.mode+"/"+(agent?"agent":"fixed"));
    }
    /** 기록에 추가된 조회·검색 단계를 바로 전달합니다. */
    private static final class ObservedTrace extends ArrayList<Object> {
        private final transient Consumer<Object> events;
        ObservedTrace(Consumer<Object> events){this.events=events;}
        @Override public boolean add(Object entry){super.add(entry);events.accept(entry);return true;}
    }
}
