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
import java.util.Map;
import lab.inquiry.intake.IntakeWire;
import java.util.Optional;

final class ReviewPlanSupport implements AutoCloseable {
    static final String CONNECTION = "{\"services\":[\"VPN\"],\"symptom\":\"자꾸 끊김\",\"errorMessage\":null}";
    static final String ACCESS = "{\"services\":[\"VPN\"],\"symptom\":\"접속이 안 됨\",\"errorMessage\":null}";
    static final List<String> VPN_ACTIONS = List.of("오류 메시지와 발생 시각, 사용 중인 네트워크를 확인합니다.",
            "재연결 뒤에도 반복되면 확인한 증상과 오류 메시지를 작업 요청 초안에 남깁니다.");
    static final String DRAFT = judgments(judgment("RB-VPN", "VPN 연결이 끊기면", "APPLIES", VPN_ACTIONS));
    static final String UNCONFIRMED = judgments(
            judgment("RB-VPN", "VPN 연결이 끊기면", "CONDITION_UNCONFIRMED", List.of()),
            judgment("RB-VPN-CERT", "", "NOT_COVERED", List.of()));
    static final String NOT_COVERED = judgments(
            judgment("RB-VPN", "", "NOT_COVERED", List.of()),
            judgment("RB-VPN-CERT", "", "NOT_COVERED", List.of()));
    static final String APPLIED_TEXT = """
            1. 접수 내용
            - 직원이 말한 서비스: VPN
            - 직원이 말한 증상: 자꾸 끊김
            - 직원이 말한 오류 메시지: 접수되지 않음

            2. 상태 조회로 확인된 것
            - VPN: 상태 normal. 현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.

            3. 운영 문서 기준으로 확인하거나 요청할 일
            - [RB-VPN] 오류 메시지와 발생 시각, 사용 중인 네트워크를 확인합니다.
            - [RB-VPN] 재연결 뒤에도 반복되면 확인한 증상과 오류 메시지를 작업 요청 초안에 남깁니다.

            4. 상태나 문서를 확인하지 못한 서비스
            - 없습니다""";
    static final String UNCONFIRMED_TEXT = """
            1. 접수 내용
            - 직원이 말한 서비스: VPN, 급여 시스템
            - 직원이 말한 증상: 접속이 안 됨
            - 직원이 말한 오류 메시지: 접수되지 않음

            2. 상태 조회로 확인된 것
            - VPN: 상태 normal. 현재 공통 장애 공지는 없다. 개인 접속 환경은 별도 확인한다.

            3. 운영 문서 기준으로 확인하거나 요청할 일
            - [RB-VPN] 문서가 다루는 경우("VPN 연결이 끊기면")인지 확인합니다.

            4. 상태나 문서를 확인하지 못한 서비스
            - 급여 시스템: 조회 자료에 없는 서비스입니다.""";

    static Map<String, Object> judgment(String id, String condition, String judgment, List<String> actions) {
        return Map.of("id", id, "condition", condition, "judgment", judgment, "actions", actions);
    }

    @SafeVarargs
    static String judgments(Map<String, Object>... documents) {
        return IntakeWire.text(Map.of("documents", List.of(documents)));
    }

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
