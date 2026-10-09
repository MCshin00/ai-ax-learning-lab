package lab.inquiry.intake;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import lab.inquiry.status.LookupResult;
import lab.inquiry.status.StatusClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IntakeSession {
    enum Failure {
        MODEL_UNAVAILABLE("모델을 호출하지 못했습니다."),
        INVALID_OUTPUT("모델의 응답이 약속한 형식과 다릅니다."),
        INVALID_INPUT("입력은 대화ID|발언 형식이어야 합니다.");

        final String message;
        Failure(String message) { this.message = message; }
    }

    record Result(String conversationId, Intake intake, List<LookupResult> statuses, Failure failure) {
        static Result failed(String id, Failure cause) { return new Result(id, null, List.of(), cause); }
    }
    record Conversation(Intake intake, List<String> utterances) {
        Conversation { utterances = List.copyOf(utterances); }
    }

    private final String model;
    private final IntakeModel.Call call;
    private final StatusClient statuses;
    private final Map<String, Conversation> conversations = new HashMap<>();

    IntakeSession(String model, IntakeModel.Call call, StatusClient statuses) {
        this.model = model;
        this.call = call;
        this.statuses = statuses;
    }

    Conversation conversation(String id) { return conversations.get(id); }

    Result accept(String line) {
        int separator = line.indexOf('|');
        if (separator < 0) return Result.failed(null, Failure.INVALID_INPUT);
        String id = line.substring(0, separator);
        String utterance = line.substring(separator + 1);
        if (id.isBlank() || utterance.isBlank()) return Result.failed(id.isBlank() ? null : id, Failure.INVALID_INPUT);
        var previous = conversations.get(id);
        var request = IntakeModel.request(model, previous == null ? null : previous.intake(), utterance);
        Intake intake;
        try { intake = IntakeModel.decode(call.complete(request)); }
        catch (OpenAIInvalidDataException e) { return Result.failed(id, Failure.INVALID_OUTPUT); }
        catch (OpenAIException e) { return Result.failed(id, Failure.MODEL_UNAVAILABLE); }
        if (intake == null) return Result.failed(id, Failure.INVALID_OUTPUT);

        var results = intake.services().stream().map(statuses::get).toList();
        var utterances = new ArrayList<>(previous == null ? List.<String>of() : previous.utterances());
        utterances.add(utterance);
        conversations.put(id, new Conversation(intake, utterances));
        return new Result(id, intake, results, null);
    }
}
