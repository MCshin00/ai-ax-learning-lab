package lab.inquiry;

import lab.inquiry.intake.IntakeModel;
import lab.inquiry.intake.IntakeSession;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.LookupResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class InquirySession {
    enum Code {
        NO_EVIDENCE("이 문의에 쓸 운영 문서를 찾지 못했습니다."),
        SEARCH_FAILED("운영 문서를 읽지 못했습니다."),
        NO_SYMPTOM("증상이나 오류 메시지가 없어 운영 문서를 찾지 않았습니다."),
        SOURCE_MISMATCH("판정이 돌려준 문서가 넘겨준 문서와 맞지 않아 초안을 버렸습니다."),
        MODEL_UNAVAILABLE("모델을 호출하지 못했습니다."),
        INVALID_OUTPUT("모델의 응답이 약속한 형식과 다릅니다.");

        final String message;
        Code(String message) { this.message = message; }
    }
    record Notice(Code code, String serviceId) {}
    record Evidence(String serviceId, List<String> queries, RunbookSearch.Result search) {}
    record Result(IntakeSession.Result intake, List<Evidence> evidence, DraftAssembler.Draft draft,
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
        return analyze(accepted, stage -> {});
    }

    Result accept(String conversationId, String utterance, Consumer<String> stages) {
        modelCalls = 0;
        return analyze(intake.accept(conversationId, utterance, stages), stages);
    }

    private Result analyze(IntakeSession.Result accepted, Consumer<String> stages) {
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
        stages.accept("SEARCH");
        for (int i = 0; i < value.services().size(); i++) {
            if (accepted.statuses().get(i).outcome() == LookupResult.Outcome.NOT_FOUND) continue;
            String service = value.services().get(i);
            evidence.add(new Evidence(service, List.of(query), search.search(service, query)));
        }
        var documents = evidence.stream().flatMap(item -> item.search().documents().stream()).toList();
        var judgments = new HashMap<String, InquiryModel.DocumentJudgment>();
        Code failure = null;
        if (!documents.isEmpty()) {
            stages.accept("DRAFT");
            var judged = model.judge(value, documents);
            failure = judged.failure();
            if (failure == null) {
                var ids = documents.stream().map(RunbookSearch.Runbook::id).toList();
                for (var item : judged.value().documents()) judgments.put(item.id(), item);
                if (judged.value().documents().size() != ids.size()
                        || judgments.size() != ids.size() || !judgments.keySet().containsAll(ids)) {
                    failure = Code.SOURCE_MISMATCH;
                } else if (judgments.values().stream().anyMatch(item ->
                        item.judgment() == InquiryModel.Judgment.APPLIES && item.actions().isEmpty()
                        || item.judgment() == InquiryModel.Judgment.CONDITION_UNCONFIRMED && item.condition().isBlank())) {
                    failure = Code.INVALID_OUTPUT;
                }
            }
        }
        if (failure != null) judgments.clear();
        addSearchNotices(evidence, notices, judgments);
        DraftAssembler.Draft draft = null;
        if (failure != null) notices.add(new Notice(failure, null));
        else if (!documents.isEmpty()) {
            var ordered = documents.stream().map(book -> judgments.get(book.id())).toList();
            draft = DraftAssembler.assemble(accepted, ordered, notices);
        }
        return new Result(accepted, evidence, draft, notices, modelCalls);
    }

    private static void addSearchNotices(List<Evidence> evidence, List<Notice> notices,
                                         Map<String, InquiryModel.DocumentJudgment> judgments) {
        for (var item : evidence) {
            if (item.search().outcome() == RunbookSearch.Outcome.FAILED) notices.add(new Notice(Code.SEARCH_FAILED, item.serviceId()));
            else if (item.search().outcome() == RunbookSearch.Outcome.NONE
                    || !judgments.isEmpty() && item.search().documents().stream()
                    .allMatch(book -> judgments.get(book.id()).judgment() == InquiryModel.Judgment.NOT_COVERED)) {
                notices.add(new Notice(Code.NO_EVIDENCE, item.serviceId()));
            }
        }
    }
}
