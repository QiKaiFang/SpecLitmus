package io.speclitmus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.speclitmus.ai.StubAiGateway;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.review.ReviewService;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewEvidenceApiTest {

    private static final String R1 = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsBackendCalculatedGlobalQuoteOffsets() throws Exception {
        MvcResult result = postReview("review-valid", new ReviewSessionStore(), R1)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode questions = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("questions");
        assertEquals(2, questions.size());
        assertQuestionEvidence(questions.get(0), 2, 4);
        assertQuestionEvidence(questions.get(1), 16, 19);
    }

    @Test
    void doesNotPublishSessionsForInvalidParagraphOrQuoteEvidence() throws Exception {
        for (String scenario : new String[]{"bad-paragraph", "bad-quote"}) {
            RecordingSessionStore store = new RecordingSessionStore();
            MvcResult result = postReview(scenario, store, R1)
                    .andExpect(status().isBadGateway())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            assertEquals("AI_OUTPUT_INVALID", body.path("code").asText());
            assertTrue(body.path("retryable").asBoolean());
            assertFalse(body.has("reviewId"));
            assertEquals(0, store.saveCalls);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postReview(
            String scenario,
            ReviewSessionStore sessionStore,
            String requirementText) throws Exception {
        ReviewService reviewService = new ReviewService(
                new RequirementParser(), new StubAiGateway(scenario), sessionStore, "review-v2");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new ReviewExceptionHandler())
                .build();
        return mockMvc.perform(post("/api/reviews")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new ReviewRequest(requirementText))));
    }

    private void assertQuestionEvidence(JsonNode question, int expectedStart, int expectedEnd) {
        assertEquals(expectedStart, question.path("quoteStart").asInt());
        assertEquals(expectedEnd, question.path("quoteEnd").asInt());
        assertEquals(question.path("quote").asText(), R1.substring(expectedStart, expectedEnd));
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
