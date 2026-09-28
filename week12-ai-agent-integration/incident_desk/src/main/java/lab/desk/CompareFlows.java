package lab.desk;
import java.nio.file.Path;
import java.util.*;

/** 같은 문의에서 검색 실행 주체 하나를 바꿔 처리 경로와 결과를 비교합니다. */
public final class CompareFlows {
    public static void main(String[] args)throws Exception {
        boolean live=Arrays.asList(args).contains("--live");
        String question=Arrays.stream(args).filter(s->!s.equals("--live")).findFirst().orElse("VPN이 자꾸 끊겨요");
        var sources=EvidenceSearch.load(Path.of("data/runbooks.json"));
        var ai=live?ModelSetup.live(System::getenv,sources):ModelSetup.scripted(sources);
        try(var operations=new OperationsClient(Path.of("data"),Path.of(".local/requests"))) {
            for(boolean agent:List.of(false,true))System.out.println(Json.write(new IncidentFlow(operations,ai,agent).analyze("compare",question)));
        }
    }
}
