package lab.inquiry;

import lab.inquiry.intake.IntakeSession;
import lab.inquiry.status.LookupResult;
import java.util.ArrayList;
import java.util.List;

final class DraftAssembler {
    record Draft(String text, List<String> sources) {}

    static Draft assemble(IntakeSession.Result accepted, List<InquiryModel.DocumentJudgment> documents,
                          List<InquirySession.Notice> notices) {
        var sources = documents.stream().filter(item -> item.judgment() != InquiryModel.Judgment.NOT_COVERED)
                .map(InquiryModel.DocumentJudgment::id).toList();
        if (sources.isEmpty()) return null;
        var intake = accepted.intake();
        var received = List.of("- 직원이 말한 서비스: " + String.join(", ", intake.services()),
                "- 직원이 말한 증상: " + (intake.symptom() == null ? "접수되지 않음" : intake.symptom()),
                "- 직원이 말한 오류 메시지: " + (intake.errorMessage() == null ? "접수되지 않음" : intake.errorMessage()));
        var statuses = new ArrayList<String>();
        var missing = new ArrayList<String>();
        for (int i = 0; i < intake.services().size(); i++) {
            String service = intake.services().get(i);
            var status = accepted.statuses().get(i);
            if (status.outcome() == LookupResult.Outcome.FOUND) {
                statuses.add("- " + service + ": 상태 " + status.state() + ". " + status.detail());
            } else if (status.outcome() == LookupResult.Outcome.NOT_FOUND) {
                missing.add("- " + service + ": 조회 자료에 없는 서비스입니다.");
            } else missing.add("- " + service + ": 상태를 조회하지 못했습니다.");
            for (var notice : notices) {
                if (service.equals(notice.serviceId()) && (notice.code() == InquirySession.Code.NO_EVIDENCE
                        || notice.code() == InquirySession.Code.SEARCH_FAILED)) {
                    missing.add("- " + service + ": " + notice.code().message);
                }
            }
        }
        var actions = new ArrayList<String>();
        for (var document : documents) {
            if (document.judgment() == InquiryModel.Judgment.APPLIES) {
                for (var action : document.actions()) actions.add("- [" + document.id() + "] " + action);
            } else if (document.judgment() == InquiryModel.Judgment.CONDITION_UNCONFIRMED) {
                actions.add("- [" + document.id() + "] 문서가 다루는 경우(\"" + document.condition() + "\")인지 확인합니다.");
            }
        }
        return new Draft(String.join("\n\n", section("1. 접수 내용", received),
                section("2. 상태 조회로 확인된 것", statuses),
                section("3. 운영 문서 기준으로 확인하거나 요청할 일", actions),
                section("4. 상태나 문서를 확인하지 못한 서비스", missing)), sources);
    }

    private static String section(String title, List<String> lines) {
        return title + "\n" + (lines.isEmpty() ? "- 없습니다" : String.join("\n", lines));
    }
}
