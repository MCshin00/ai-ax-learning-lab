package lab.inquiry;

import com.openai.errors.OpenAIException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.RequestData;
import lab.inquiry.status.RequestResult;
import lab.inquiry.status.RequestWire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static lab.inquiry.ReviewPlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewPlanTest {
    @TempDir Path directory;

    @Test void editedTextUsesStoredReviewOnlyAfterConfirmation() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION, DRAFT)) {
            var result = app.accept("VPN이 자꾸 끊겨요");
            assertEquals(0, app.saves.size());
            assertEquals("7b1c0c1e-0000-4000-8000-000000000001", result.review().reviewId());
            var saved = app.session.save(result.review().reviewId(), " 담당자가 고친 본문\n");
            assertEquals(SAVED, IntakeWire.text(RequestWire.fields(saved)));
            assertEquals(List.of(new RequestData(result.review().reviewId(), " 담당자가 고친 본문\n",
                    List.of(new RequestData.Service("VPN", 1)), List.of("RB-VPN"), List.of())), app.saves);
        }
    }

    @Test void unknownServiceRemainsUnprocessed() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION.replace("[\"VPN\"]", "[\"VPN\",\"급여 시스템\"]"), DRAFT)) {
            var result = app.accept("VPN과 급여 시스템이 안 돼요");
            assertEquals("[{\"serviceId\":\"급여 시스템\",\"code\":\"NOT_FOUND\"}]",
                    IntakeWire.text(RequestWire.unprocessed(result.review().unprocessed())));
        }
    }

    @Test void noSymptomReasonSurvivesReviewSaveAndRead() throws Exception {
        try (var app = new ReviewPlanSupport(directory,
                "{\"services\":[\"VPN\"],\"symptom\":null,\"errorMessage\":null}")) {
            var result = app.accept("VPN이요");
            assertEquals("{\"reviewId\":\"7b1c0c1e-0000-4000-8000-000000000001\","
                    + "\"services\":[{\"serviceId\":\"VPN\",\"revision\":1}],\"unprocessed\":[{\"code\":\"NO_SYMPTOM\"}]}",
                    IntakeWire.text(HttpWire.fields(result).get("review")));
            var saved = app.session.save(result.review().reviewId(), "담당자가 고친 본문");
            assertEquals(SAVED, IntakeWire.text(RequestWire.fields(saved)));
            var found = RequestWire.fields(app.client.getRequest(saved.requestId()));
            assertEquals("{\"outcome\":\"FOUND\",\"requestId\":\"REQ-0001\","
                    + "\"reviewId\":\"7b1c0c1e-0000-4000-8000-000000000001\",\"text\":\"담당자가 고친 본문\","
                    + "\"services\":[{\"serviceId\":\"VPN\",\"revision\":1}],\"sources\":[],"
                    + "\"unprocessed\":[{\"code\":\"NO_SYMPTOM\"}]}", IntakeWire.text(found));
            var stored = new ObjectMapper().readTree(Files.readString(directory.resolve("requests.json")));
            assertEquals("[{\"code\":\"NO_SYMPTOM\"}]", stored.at("/0/request/unprocessed").toString());
        }
    }

    @Test void sourceMismatchFollowsUnprocessedServices() throws Exception {
        try (var app = new ReviewPlanSupport(directory,
                CONNECTION.replace("[\"VPN\"]", "[\"VPN\",\"급여 시스템\"]"),
                DRAFT.replace("RB-VPN", "RB-UNKNOWN"))) {
            var result = app.accept("VPN과 급여 시스템이 자꾸 끊겨요");
            assertEquals("[{\"serviceId\":\"급여 시스템\",\"code\":\"NOT_FOUND\"},{\"code\":\"SOURCE_MISMATCH\"}]",
                    IntakeWire.text(RequestWire.unprocessed(result.review().unprocessed())));
        }
    }

    @Test void noDraftStillAllowsReviewWithEmptySources() throws Exception {
        try (var app = new ReviewPlanSupport(directory, ACCESS, NOT_COVERED)) {
            var result = app.accept("VPN 접속이 안 돼요");
            assertNull(result.inquiry().draft());
            assertEquals("[{\"serviceId\":\"VPN\",\"code\":\"NO_EVIDENCE\"}]",
                    IntakeWire.text(RequestWire.unprocessed(result.review().unprocessed())));
            assertEquals(RequestResult.Outcome.SAVED, app.session.save(result.review().reviewId(), "직접 쓴 본문").outcome());
            assertEquals(List.of(), app.saves.get(0).sources());
            var found = app.client.getRequest("REQ-0001");
            assertEquals("직접 쓴 본문", found.request().text());
            assertEquals(List.of(new RequestData.Unprocessed("VPN", "NO_EVIDENCE")), found.request().unprocessed());
        }
    }

    @Test void unconfirmedSourceSurvivesReviewSaveAndRead() throws Exception {
        try (var app = new ReviewPlanSupport(directory,
                ACCESS.replace("[\"VPN\"]", "[\"VPN\",\"급여 시스템\"]"), UNCONFIRMED)) {
            var result = app.accept("VPN과 급여 시스템 접속이 안 돼요");
            assertEquals(List.of("RB-VPN"), result.review().sources());
            assertEquals(List.of(new RequestData.Unprocessed("급여 시스템", "NOT_FOUND")), result.review().unprocessed());
            var saved = app.session.save(result.review().reviewId(), result.inquiry().draft().text());
            assertEquals(RequestResult.Outcome.SAVED, saved.outcome());
            var found = app.client.getRequest(saved.requestId());
            assertEquals(List.of("RB-VPN"), found.request().sources());
            assertEquals(UNCONFIRMED_TEXT, found.request().text());
        }
    }

    @Test void partiallyApplicableServiceRemainsUnprocessedThroughSaveAndRead() throws Exception {
        try (var app = new ReviewPlanSupport(directory,
                CONNECTION.replace("[\"VPN\"]", "[\"VPN\",\"MAIL\"]"), judgments(
                        judgment("RB-VPN", "VPN 연결이 끊기면", "APPLIES", VPN_ACTIONS),
                        judgment("RB-MAIL", "", "NOT_COVERED", List.of())))) {
            var result = app.accept("VPN과 메일이 자꾸 끊겨요");
            var missing = List.of(new RequestData.Unprocessed("MAIL", "NO_EVIDENCE"));
            assertEquals(missing, result.review().unprocessed());
            assertEquals(List.of("RB-VPN"), result.review().sources());
            var saved = app.session.save(result.review().reviewId(), result.inquiry().draft().text());
            assertEquals(RequestResult.Outcome.SAVED, saved.outcome());
            var found = app.client.getRequest(saved.requestId());
            assertEquals(missing, found.request().unprocessed());
            assertEquals(result.inquiry().draft().text(), found.request().text());
            assertEquals(List.of("RB-VPN"), found.request().sources());
        }
    }

    @Test void secondSuccessfulIntakeInvalidatesFirstReview() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION, DRAFT, CONNECTION, DRAFT)) {
            var first = app.accept("VPN이 자꾸 끊겨요");
            var second = app.accept("여전히 끊겨요");
            assertNotEquals(first.review().reviewId(), second.review().reviewId());
            assertEquals(STALE, IntakeWire.text(RequestWire.fields(app.session.save(first.review().reviewId(), "본문"))));
            assertEquals(0, app.saves.size());
        }
    }

    @Test void allStatusFailuresHaveNoReview() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION, DRAFT)) {
            Files.writeString(directory.resolve("services.json"), "not json");
            var result = app.accept("VPN이 자꾸 끊겨요");
            assertEquals("READY", HttpWire.fields(result).get("outcome"));
            assertFalse(HttpWire.fields(result).containsKey("review"));
            assertNotNull(result.inquiry().draft());
        }
    }

    @Test void intakeFailureKeepsPreviousReviewCurrent() throws Exception {
        try (var app = new ReviewPlanSupport(directory, CONNECTION, DRAFT, new OpenAIException("prepared failure"))) {
            var first = app.accept("VPN이 자꾸 끊겨요");
            assertEquals("FAILED", HttpWire.fields(app.accept("추가 발언")).get("outcome"));
            assertEquals(SAVED, IntakeWire.text(RequestWire.fields(app.session.save(first.review().reviewId(), "본문"))));
            assertEquals(1, app.saves.size());
        }
    }
}
