package io.speclitmus.review;

import io.speclitmus.ai.AiGateway;
import io.speclitmus.api.ReviewRequestException;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewInputTest {

    private CountingAiGateway aiGateway;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        aiGateway = new CountingAiGateway();
        reviewService = new ReviewService(
                new RequirementParser(), aiGateway, new ReviewSessionStore(), "review-v2");
    }

    @Test
    void rejectsBlankInputBeforeCallingTheModel() {
        ReviewRequestException exception = assertThrows(ReviewRequestException.class,
                () -> reviewService.review(" \t\n"));

        assertEquals("需求文本不能为空。", exception.getMessage());
        assertEquals(0, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
    }

    @Test
    void rejectsInputLongerThanTwoThousandCharactersBeforeCallingTheModel() {
        ReviewRequestException exception = assertThrows(ReviewRequestException.class,
                () -> reviewService.review("测".repeat(2_001)));

        assertEquals("需求文本不能超过 2,000 个字符。", exception.getMessage());
        assertEquals(0, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
    }

    @Test
    void acceptsExactlyTwoThousandCharactersAndAllowsAnEmptyReview() {
        ReviewResponse response = reviewService.review("测".repeat(2_000));

        assertTrue(response.questions().isEmpty());
        assertFalse(response.reviewId().isBlank());
        assertEquals(1, aiGateway.reviewCalls);
        assertEquals(0, aiGateway.repairCalls);
    }

    private static class CountingAiGateway implements AiGateway {

        private int reviewCalls;
        private int repairCalls;

        @Override
        public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
            reviewCalls++;
            return List.of();
        }

        @Override
        public List<ReviewQuestionDraft> repairReview(RequirementSnapshot snapshot, String validationError) {
            repairCalls++;
            return List.of();
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }
}
