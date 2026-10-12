package lab.inquiry;

import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import lab.inquiry.intake.IntakeModel;
import lab.inquiry.status.RequestData;
import lab.inquiry.status.RequestTestSupport;
import lab.inquiry.status.StatusClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ReviewPlanSupport implements AutoCloseable {
    static final String CONNECTION = "{\"services\":[\"VPN\"],\"symptom\":\"연결이 자꾸 끊김\",\"errorMessage\":null}";
    static final String ACCESS = "{\"services\":[\"VPN\"],\"symptom\":\"접속이 안 됨\",\"errorMessage\":null}";
    static final String DRAFT = "{\"text\":\"초안 본문\",\"sources\":[\"RB-VPN\"]}";
    static final String SAVED = "{\"outcome\":\"SAVED\",\"requestId\":\"REQ-0001\"}";
    static final String STALE = "{\"outcome\":\"STALE_REVIEW\",\"code\":\"REVIEW_NOT_CURRENT\",\"message\":\"가장 최근 검토본이 아닙니다. 다시 문의해 검토해 주세요.\"}";
    final StatusClient client;
    final ReviewSession session;
    final List<RequestData> saves = new ArrayList<>();

    ReviewPlanSupport(Path directory, Object... replies) throws Exception { this(directory, prepared(replies)); }

    ReviewPlanSupport(Path directory, IntakeModel.Call call) throws Exception {
        client = RequestTestSupport.client(directory);
        var inquiries = new InquirySession("test-model", call, client::get, directory);
        int[] next = {0};
        session = new ReviewSession(inquiries, data -> { saves.add(data); return client.save(data); },
                () -> "7b1c0c1e-0000-4000-8000-" + String.format(java.util.Locale.ROOT, "%012d", ++next[0]));
    }

    ReviewSession.Result accept(String utterance) { return session.accept("a", utterance, stage -> {}); }

    static IntakeModel.Call prepared(Object... replies) {
        int[] next = {0};
        return request -> {
            var reply = replies[next[0]++];
            if (reply instanceof OpenAIException exception) throw exception;
            return completion((String) reply);
        };
    }

    static ChatCompletion completion(String content) {
        return ChatCompletion.builder().id("prepared").created(0).model("test-model")
                .addChoice(ChatCompletion.Choice.builder().index(0).logprobs(Optional.empty())
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .message(ChatCompletionMessage.builder().content(content).refusal(Optional.empty()).build()).build()).build();
    }

    @Override public void close() { client.close(); }
}
