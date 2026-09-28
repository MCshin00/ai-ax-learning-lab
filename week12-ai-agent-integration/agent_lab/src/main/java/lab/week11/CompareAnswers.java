package lab.week11;

import java.nio.file.Path;

/** 동일 입력에서 문맥 범위와 추가 생성의 차이를 확인합니다. */
public final class CompareAnswers {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--live")))
            throw new IllegalArgumentException("실제 모델 비교는 --live로 실행하세요.");
        var ai = args.length == 1 ? ModelSetup.live(System::getenv, Application.policies())
            : ModelSetup.scripted(Application.policies());
        for (String variant : new String[]{"excerpt_only", "revise_once", "full_context"}) {
            var app = new Consultation(ai, new OrderLookup(Path.of("data/orders.json")), Application.policies(),
                6, variant.equals("revise_once"), variant.equals("full_context") ? "full" : "excerpt");
            var result = app.reply(variant, "A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.");
            System.out.println(Json.write(result));
        }
    }
}
