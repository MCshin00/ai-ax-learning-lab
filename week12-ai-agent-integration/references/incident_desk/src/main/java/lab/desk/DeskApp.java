package lab.desk;

import java.nio.file.Path;
import java.util.*;

/** IDE 콘솔에서 문의와 검토 후 저장을 구분해 실행합니다. */
public final class DeskApp {
    static com.fasterxml.jackson.databind.JsonNode handle(ReviewDesk desk,String line) {
        if(line.startsWith("/save|")) {
            var p=line.split("\\|",5);
            if(p.length!=5)throw new IllegalArgumentException("대화 ID·검토 ID·요청 ID·본문이 필요합니다.");
            return desk.save(p[1],p[2],p[3],p[4]);
        }
        var p=line.split("\\|",2);if(p.length!=2)throw new IllegalArgumentException("대화ID|내용으로 입력하세요.");
        return Json.tree(desk.analyze(p[0],p[1]));
    }
    public static void main(String[] args)throws Exception {
        var options=Set.of(args);if(!Set.of("--live","--fixed").containsAll(options))throw new IllegalArgumentException("--live, --fixed를 사용하세요.");
        var sources=EvidenceSearch.load(Path.of("data/runbooks.json"));
        var ai=options.contains("--live")?ModelSetup.live(System::getenv,sources):ModelSetup.scripted(sources);
        try(var operations=new OperationsClient(Path.of("data"),Path.of(".local/requests"))) {
            var desk=new ReviewDesk(new IncidentFlow(operations,ai,!options.contains("--fixed")),operations);
            var scanner=new Scanner(System.in,java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("모드: "+ai.mode+". 문의: 대화ID|내용 / 저장: /save|대화ID|검토ID|요청ID|검토한 본문 / 종료: /quit");
            while(scanner.hasNextLine()) {
                String line=scanner.nextLine();if(line.equals("/quit"))break;
                try {
                    System.out.println(Json.write(handle(desk,line)));
                }catch(IllegalArgumentException e){System.out.println("입력 형식과 제공 사례를 확인하세요.");}
            }
        }
    }
}
