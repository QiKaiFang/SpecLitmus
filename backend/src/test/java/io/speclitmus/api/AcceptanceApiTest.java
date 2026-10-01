package io.speclitmus.api;

import io.speclitmus.acceptance.AcceptanceEvidenceValidator;
import io.speclitmus.acceptance.AcceptanceService;
import io.speclitmus.ai.StubAiGateway;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.review.ReviewService;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcceptanceApiTest {

    private static final String R1 = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";

    @Test
    void supportsPartialThenCompleteAnswersForTheSameReview() {
        Fixture fixture = fixture("workflow");
        ReviewResponse review = fixture.reviewService().review(R1);

        AcceptanceResponse partial = fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(new AnswerRequest("Q001", "按北京时间自然日重置。"))));
        assertEquals(1, partial.cases().size());
        assertEquals(List.of("Q002"), partial.pendingQuestionIds());
        assertEquals("Q001", partial.cases().getFirst().basis().get(1).sourceId());

        AcceptanceResponse complete = fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(
                        new AnswerRequest("Q001", "按北京时间自然日重置。"),
                        new AnswerRequest("Q002", "注册当天属于新用户，额外奖励每个账号仅一次。"))));
        assertEquals(2, complete.cases().size());
        assertEquals(List.of(), complete.pendingQuestionIds());
    }

    @Test
    void rejectsInvalidAnswersBeforeCallingTheAcceptanceModel() {
        Fixture fixture = fixture("workflow");
        ReviewResponse review = fixture.reviewService().review(R1);
        int callsAfterReview = fixture.gateway().totalCallCount();

        assertThrows(ReviewRequestException.class, () -> fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(new AnswerRequest("Q999", "未知问题")))));
        assertThrows(ReviewRequestException.class, () -> fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(
                        new AnswerRequest("Q001", "回答一"),
                        new AnswerRequest("Q001", "回答二")))));
        assertThrows(ReviewRequestException.class, () -> fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(new AnswerRequest("Q001", "x".repeat(501))))));
        assertEquals(callsAfterReview, fixture.gateway().totalCallCount());
    }

    @Test
    void treatsWhitespaceAsUnansweredAndCalculatesPendingOnTheServer() {
        Fixture fixture = fixture("workflow");
        ReviewResponse review = fixture.reviewService().review(R1);

        AcceptanceResponse response = fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(new AnswerRequest("Q001", " \n\t "))));

        assertEquals(List.of(), response.cases());
        assertEquals(List.of("Q001", "Q002"), response.pendingQuestionIds());
        assertEquals(1, fixture.gateway().currentRequestCallCount());
    }

    @Test
    void returnsGoneForAnExpiredReviewWithoutCallingTheModel() throws Exception {
        Fixture fixture = fixture("workflow");
        MockMvc mockMvc = fixture.mockMvc();

        mockMvc.perform(post("/api/reviews/missing/acceptance")
                        .contentType("application/json")
                        .content("{\"answers\":[]}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertEquals(0, fixture.gateway().totalCallCount());
    }

    @Test
    void repairsOnceThenRejectsAcceptanceWithInventedBasis() {
        Fixture fixture = fixture("acceptance-bad-basis");
        ReviewResponse review = fixture.reviewService().review(R1);

        assertThrows(InvalidModelOutputException.class, () -> fixture.acceptanceService().generate(
                review.reviewId(),
                new AcceptanceRequest(List.of(new AnswerRequest("Q001", "按自然日重置。")))));
        assertEquals(2, fixture.gateway().currentRequestCallCount());
    }

    @Test
    void allowsAnEmptyReviewToProduceZeroCasesWithAnExplanation() {
        Fixture fixture = fixture("workflow-empty");
        ReviewResponse review = fixture.reviewService().review(R1);

        AcceptanceResponse response = fixture.acceptanceService().generate(
                review.reviewId(), new AcceptanceRequest(List.of()));

        assertEquals(List.of(), response.cases());
        assertEquals(List.of(), response.pendingQuestionIds());
        assertEquals(1, response.warnings().size());
    }

    @Test
    void evictsTheOldestReviewAfterTwentySessionsButKeepsTheLatestUsable() throws Exception {
        Fixture fixture = fixture("workflow-empty");
        ReviewResponse first = fixture.reviewService().review(R1);
        ReviewResponse latest = first;
        for (int index = 0; index < 20; index++) {
            latest = fixture.reviewService().review(R1);
        }

        fixture.mockMvc().perform(post("/api/reviews/{reviewId}/acceptance", first.reviewId())
                        .contentType("application/json")
                        .content("{\"answers\":[]}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        fixture.mockMvc().perform(post("/api/reviews/{reviewId}/acceptance", latest.reviewId())
                        .contentType("application/json")
                        .content("{\"answers\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isEmpty())
                .andExpect(jsonPath("$.warnings[0]").isNotEmpty());
    }

    private Fixture fixture(String scenario) {
        ReviewSessionStore store = new ReviewSessionStore();
        StubAiGateway gateway = new StubAiGateway(scenario);
        ReviewService reviewService = new ReviewService(
                new RequirementParser(), gateway, store, "review-v2");
        AcceptanceService acceptanceService = new AcceptanceService(
                store, gateway, new AcceptanceEvidenceValidator(), "acceptance-v1");
        ReviewController controller = new ReviewController(reviewService, acceptanceService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ReviewExceptionHandler())
                .build();
        return new Fixture(gateway, reviewService, acceptanceService, mockMvc);
    }

    private record Fixture(
            StubAiGateway gateway,
            ReviewService reviewService,
            AcceptanceService acceptanceService,
            MockMvc mockMvc) {
    }
}
