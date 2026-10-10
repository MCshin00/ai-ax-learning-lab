package lab.inquiry;

import lab.inquiry.intake.IntakeModel;
import lab.inquiry.intake.IntakeSession;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.LookupResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

final class InquirySession {
    enum Code {
        NO_EVIDENCE("운영 문서에서 근거를 찾지 못했습니다."),
        SEARCH_FAILED("운영 문서를 읽지 못했습니다."),
        NO_SYMPTOM("증상이나 오류 메시지가 없어 운영 문서를 찾지 않았습니다."),
        SOURCE_MISMATCH("초안의 출처가 찾은 문서와 맞지 않아 초안을 버렸습니다."),
        MODEL_UNAVAILABLE("모델을 호출하지 못했습니다."),
        INVALID_OUTPUT("모델의 응답이 약속한 형식과 다릅니다.");

        final String message;
        Code(String message) { this.message = message; }
    }
    record Notice(Code code, String serviceId) {}
    record Evidence(String serviceId, List<String> queries, RunbookSearch.Result search) {}
    record Result(IntakeSession.Result intake, List<Evidence> evidence, InquiryModel.Draft draft,
                  List<Notice> notices, int modelCalls) {}

    private final IntakeSession intake;
    private final RunbookSearch search;
    private final InquiryModel model;
    private int modelCalls;

    InquirySession(String modelName, IntakeModel.Call call, Function<String, LookupResult> statuses, Path directory) {
        IntakeModel.Call counted = request -> { modelCalls++; return call.complete(request); };
        intake = new IntakeSession(modelName, counted, statuses);
        model = new InquiryModel(modelName, counted);
        search = new RunbookSearch(directory);
    }

    Result accept(String line) {
        modelCalls = 0;
        var accepted = intake.accept(line);
        var evidence = new ArrayList<Evidence>();
        var notices = new ArrayList<Notice>();
        if (accepted.failure() != null || IntakeWire.needsInput(accepted)) {
            return new Result(accepted, evidence, null, notices, modelCalls);
        }
        var value = accepted.intake();
        String query = String.join(" ", Stream.of(value.symptom(), value.errorMessage())
                .filter(part -> part != null).toList());
        if (query.isEmpty()) {
            notices.add(new Notice(Code.NO_SYMPTOM, null));
            return new Result(accepted, evidence, null, notices, modelCalls);
        }
        for (int i = 0; i < value.services().size(); i++) {
            if (accepted.statuses().get(i).outcome() == LookupResult.Outcome.NOT_FOUND) continue;
            String service = value.services().get(i);
            evidence.add(new Evidence(service, List.of(query), search.search(service, query)));
        }
        var targets = evidence.stream().filter(item -> item.search().outcome() == RunbookSearch.Outcome.NONE
                && item.search().hasServiceDocuments()).map(Evidence::serviceId).toList();
        if (!targets.isEmpty()) {
            var rewritten = model.rewrite(value, targets);
            if (rewritten.failure() != null) {
                addSearchNotices(evidence, notices, targets);
                notices.add(new Notice(rewritten.failure(), null));
                return new Result(accepted, evidence, null, notices, modelCalls);
            }
            for (int i = 0; i < evidence.size(); i++) {
                var item = evidence.get(i);
                if (targets.contains(item.serviceId())) {
                    String nextQuery = rewritten.value().query();
                    evidence.set(i, new Evidence(item.serviceId(), List.of(query, nextQuery), search.search(item.serviceId(), nextQuery)));
                }
            }
        }
        addSearchNotices(evidence, notices, List.of());
        var documents = evidence.stream().flatMap(item -> item.search().documents().stream()).toList();
        InquiryModel.Draft draft = null;
        if (!documents.isEmpty()) {
            var drafted = model.draft(value, accepted.statuses(), documents);
            if (drafted.failure() != null) notices.add(new Notice(drafted.failure(), null));
            else {
                var ids = documents.stream().map(RunbookSearch.Runbook::id).toList();
                if (drafted.value().sources().isEmpty() || !ids.containsAll(drafted.value().sources())) {
                    notices.add(new Notice(Code.SOURCE_MISMATCH, null));
                } else draft = drafted.value();
            }
        }
        return new Result(accepted, evidence, draft, notices, modelCalls);
    }

    private static void addSearchNotices(List<Evidence> evidence, List<Notice> notices, List<String> pending) {
        for (var item : evidence) {
            if (item.search().outcome() == RunbookSearch.Outcome.FAILED) notices.add(new Notice(Code.SEARCH_FAILED, item.serviceId()));
            else if (item.search().outcome() == RunbookSearch.Outcome.NONE && !pending.contains(item.serviceId())) {
                notices.add(new Notice(Code.NO_EVIDENCE, item.serviceId()));
            }
        }
    }
}
