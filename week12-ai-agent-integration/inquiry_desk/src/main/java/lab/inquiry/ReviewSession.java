package lab.inquiry;

import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.LookupResult;
import lab.inquiry.status.RequestData;
import lab.inquiry.status.RequestResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class ReviewSession {
    record Review(String reviewId, List<RequestData.Service> services, List<String> sources,
                  List<RequestData.Unprocessed> unprocessed) {}
    record Result(InquirySession.Result inquiry, Review review) {}
    private final InquirySession inquiries;
    private final Function<RequestData, RequestResult> save;
    private final Supplier<String> ids;
    private final Map<String, Review> reviews = new HashMap<>();

    ReviewSession(InquirySession inquiries, Function<RequestData, RequestResult> save) {
        this(inquiries, save, () -> UUID.randomUUID().toString());
    }

    ReviewSession(InquirySession inquiries, Function<RequestData, RequestResult> save, Supplier<String> ids) {
        this.inquiries = inquiries;
        this.save = save;
        this.ids = ids;
    }

    Result accept(String conversationId, String utterance, Consumer<String> stages) {
        var result = inquiries.accept(conversationId, utterance, stages);
        if (result.intake().failure() != null) return new Result(result, null);
        reviews.remove(conversationId);
        if (IntakeWire.needsInput(result.intake())) return new Result(result, null);
        var services = new ArrayList<RequestData.Service>();
        var unprocessed = new ArrayList<RequestData.Unprocessed>();
        for (int i = 0; i < result.intake().intake().services().size(); i++) {
            String service = result.intake().intake().services().get(i);
            var status = result.intake().statuses().get(i);
            if (status.outcome() == LookupResult.Outcome.FOUND) {
                services.add(new RequestData.Service(service, status.revision()));
            } else {
                unprocessed.add(new RequestData.Unprocessed(service,
                        status.outcome() == LookupResult.Outcome.NOT_FOUND ? "NOT_FOUND" : "UNAVAILABLE"));
            }
            for (var notice : result.notices()) {
                if (service.equals(notice.serviceId())
                        && (notice.code() == InquirySession.Code.NO_EVIDENCE || notice.code() == InquirySession.Code.SEARCH_FAILED)) {
                    unprocessed.add(new RequestData.Unprocessed(service, notice.code().name()));
                }
            }
        }
        if (services.isEmpty()) return new Result(result, null);
        for (var notice : result.notices()) {
            if (notice.serviceId() == null) {
                unprocessed.add(new RequestData.Unprocessed(null, notice.code().name()));
            }
        }
        var review = new Review(ids.get(), List.copyOf(services),
                result.draft() == null ? List.of() : result.draft().sources(), List.copyOf(unprocessed));
        reviews.put(conversationId, review);
        return new Result(result, review);
    }

    RequestResult save(String reviewId, String text) {
        var review = reviews.values().stream().filter(value -> value.reviewId().equals(reviewId)).findFirst();
        if (review.isEmpty()) return RequestResult.error(RequestResult.Outcome.STALE_REVIEW, null,
                "REVIEW_NOT_CURRENT", "가장 최근 검토본이 아닙니다. 다시 문의해 검토해 주세요.");
        var value = review.get();
        return save.apply(new RequestData(reviewId, text, value.services(), value.sources(), value.unprocessed()));
    }
}
