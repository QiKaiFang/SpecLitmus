package io.speclitmus.review;

import io.speclitmus.ai.AiGateway;
import io.speclitmus.ai.AiModelTimeoutException;
import io.speclitmus.ai.AiModelUnavailableException;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceRetryTest {

    private static final String REQUIREMENT = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";

    private RecordingAiGateway aiGateway;
    private RecordingSessionStore sessionStore;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        aiGateway = new RecordingAiGateway();
        sessionStore = new RecordingSessionStore();
        reviewService = new ReviewService(new RequirementParser(), aiGateway, sessionStore, "review-v2");
    }

    @Test
    void repairsOnceWhenInitialParagraphIdAndQuoteAreInvalid() {
        aiGateway.initialDrafts = List.of(invalidDraft());
        aiGateway.repairedDrafts = List.of(validDraft());

        ReviewResponse response = reviewService.review(REQUIREMENT);

        assertEquals(1, response.questions().size());
        assertEquals("P001", response.questions().getFirst().paragraphId());
        assertEquals("每天", response.questions().getFirst().quote());
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(1, aiGateway.repairCalls);
        assertTrue(aiGateway.repairReason.contains("quote matched no paragraph"));
        assertEquals(1, sessionStore.saveCalls);
        assertNotNull(sessionStore.find(response.reviewId()).orElseThrow());
    }

    @Test
    void repairsOnceWhenParagraphIdIsValidButQuoteMatchesNoParagraph() {
        aiGateway.initialDrafts = List.of(new ReviewQuestionDraft(QuestionType.AMBIGUITY,
                "P001", "不存在于需求里的引用", "跨到次日再次领取时", "每天如何计算？",
                List.of("自然日", "间隔 24 小时")));
        aiGateway.repairedDrafts = List.of(validDraft());

        ReviewResponse response = reviewService.review(REQUIREMENT);

        assertEquals("P001", response.questions().getFirst().paragraphId());
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(1, aiGateway.repairCalls);
        assertTrue(aiGateway.repairReason.contains("quote matched no paragraph"));
        assertEquals(1, sessionStore.saveCalls);
    }

    @Test
    void correctsWrongParagraphIdWhenQuoteUniquelyMatchesAnotherParagraph() {
        aiGateway.initialDrafts = List.of(new ReviewQuestionDraft(QuestionType.AMBIGUITY,
                "P001", "新用户", "新用户领取优惠券时", "新用户额度如何计算？",
                List.of("额外一张", "共享一次额度")));

        ReviewResponse response = reviewService.review(REQUIREMENT);

        assertEquals("P002", response.questions().getFirst().paragraphId());
        assertEquals("新用户", response.questions().getFirst().quote());
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
        assertEquals(1, sessionStore.saveCalls);
    }

    @Test
    void rejectsAfterTheRepairIsAlsoInvalid() {
        aiGateway.initialDrafts = List.of(invalidDraft());
        aiGateway.repairedDrafts = List.of(invalidDraft());

        InvalidModelOutputException exception = assertThrows(InvalidModelOutputException.class,
                () -> reviewService.review(REQUIREMENT));

        assertTrue(exception.getMessage().contains("quote matched no paragraph"));
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(1, aiGateway.repairCalls);
        assertEquals(0, sessionStore.saveCalls);
    }

    @Test
    void validInitialOutputUsesOneModelCall() {
        aiGateway.initialDrafts = List.of(validDraft());

        ReviewResponse response = reviewService.review(REQUIREMENT);

        assertEquals("P001", response.questions().getFirst().paragraphId());
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
        assertEquals(1, sessionStore.saveCalls);
        assertNotNull(sessionStore.find(response.reviewId()).orElseThrow());
    }

    @Test
    void timeoutDoesNotTriggerRepair() {
        aiGateway.initialFailure = new AiModelTimeoutException(new RuntimeException("timed out"));

        assertThrows(AiModelTimeoutException.class, () -> reviewService.review(REQUIREMENT));

        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
        assertEquals(0, sessionStore.saveCalls);
    }

    @Test
    void unavailableModelDoesNotTriggerRepair() {
        aiGateway.initialFailure = new AiModelUnavailableException();

        assertThrows(AiModelUnavailableException.class, () -> reviewService.review(REQUIREMENT));

        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
        assertEquals(0, sessionStore.saveCalls);
    }

    private ReviewQuestionDraft validDraft() {
        return new ReviewQuestionDraft(QuestionType.AMBIGUITY, "P001", "每天",
                "跨到次日再次领取时", "每天如何计算？", List.of("自然日", "间隔 24 小时"));
    }

    private ReviewQuestionDraft invalidDraft() {
        return new ReviewQuestionDraft(QuestionType.AMBIGUITY, "not-a-paragraph", "不存在于需求里的引用",
                "跨到次日再次领取时", "每天如何计算？", List.of("自然日", "间隔 24 小时"));
    }

    private static class RecordingAiGateway implements AiGateway {

        private List<ReviewQuestionDraft> initialDrafts;
        private List<ReviewQuestionDraft> repairedDrafts;
        private RuntimeException initialFailure;
        private int reviewCalls;
        private int repairCalls;
        private String repairReason;

        @Override
        public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
            reviewCalls++;
            if (initialFailure != null) {
                throw initialFailure;
            }
            return initialDrafts;
        }

        @Override
        public List<ReviewQuestionDraft> repairReview(RequirementSnapshot snapshot, String validationError) {
            repairCalls++;
            repairReason = validationError;
            return repairedDrafts;
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }

    private static class RecordingSessionStore extends ReviewSessionStore {

        private int saveCalls;

        @Override
        public void save(ReviewResponse review) {
            saveCalls++;
            super.save(review);
        }
    }
}
