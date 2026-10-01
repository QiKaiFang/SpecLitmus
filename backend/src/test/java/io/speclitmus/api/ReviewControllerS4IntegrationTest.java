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

class ReviewControllerS4IntegrationTest {

    private static final String R1 = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repairsInvalidFixtureOutputOnceAndPublishesOnlyTheRepairedReview() throws Exception {
        StubAiGateway gateway = new StubAiGateway("repair-ok");
        ReviewSessionStore store = new ReviewSessionStore();
        MockMvc mockMvc = mockMvc(gateway, store);

        MvcResult result = postReview(mockMvc)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(2, body.path("questions").size());
        assertEquals("P001", body.path("questions").get(0).path("paragraphId").asText());
        assertEquals(2, gateway.currentRequestCallCount());
        assertEquals(2, gateway.totalCallCount());
        assertTrue(store.find(body.path("reviewId").asText()).isPresent());
    }

    @Test
    void stopsAfterOneRepairAndResetsTheSequenceForTheNextBusinessRequest() throws Exception {
        StubAiGateway gateway = new StubAiGateway("repair-fail");
        RecordingSessionStore store = new RecordingSessionStore();
        MockMvc mockMvc = mockMvc(gateway, store);

        MvcResult first = postReview(mockMvc)
                .andExpect(status().isBadGateway())
                .andReturn();
        assertError(first, "AI_OUTPUT_INVALID");
        assertEquals(2, gateway.currentRequestCallCount());
        assertEquals(2, gateway.totalCallCount());

        MvcResult second = postReview(mockMvc)
                .andExpect(status().isBadGateway())
                .andReturn();
        assertError(second, "AI_OUTPUT_INVALID");
        assertEquals(2, gateway.currentRequestCallCount());
        assertEquals(4, gateway.totalCallCount());
        assertEquals(0, store.saveCalls);
    }

    @Test
    void validOutputUsesOneCallWithoutRepair() throws Exception {
        StubAiGateway gateway = new StubAiGateway("review-valid");

        MvcResult result = postReview(mockMvc(gateway, new ReviewSessionStore()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(2, objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("questions").size());
        assertEquals(1, gateway.currentRequestCallCount());
        assertEquals(1, gateway.totalCallCount());
    }

    @Test
    void timeoutAndUnavailableDoNotSpendARepairCall() throws Exception {
        for (String scenario : new String[]{"timeout", "unavailable"}) {
            StubAiGateway gateway = new StubAiGateway(scenario);
            MvcResult result = postReview(mockMvc(gateway, new ReviewSessionStore()))
                    .andReturn();

            assertEquals(scenario.equals("timeout") ? 504 : 503,
                    result.getResponse().getStatus());
            assertEquals(1, gateway.currentRequestCallCount());
            assertEquals(1, gateway.totalCallCount());
        }
    }

    @Test
    void invalidCitationStillGetsOnlyOneRepairOpportunity() throws Exception {
        StubAiGateway gateway = new StubAiGateway("bad-quote");

        MvcResult result = postReview(mockMvc(gateway, new ReviewSessionStore()))
                .andExpect(status().isBadGateway())
                .andReturn();

        assertError(result, "AI_OUTPUT_INVALID");
        assertEquals(2, gateway.currentRequestCallCount());
        assertEquals(2, gateway.totalCallCount());
    }

    private org.springframework.test.web.servlet.ResultActions postReview(MockMvc mockMvc) throws Exception {
        return mockMvc.perform(post("/api/reviews")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new ReviewRequest(R1))));
    }

    private MockMvc mockMvc(StubAiGateway gateway, ReviewSessionStore store) {
        ReviewService reviewService = new ReviewService(
                new RequirementParser(), gateway, store, "review-v2");
        return MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new ReviewExceptionHandler())
                .build();
    }

    private void assertError(MvcResult result, String expectedCode) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(expectedCode, body.path("code").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertTrue(body.path("retryable").asBoolean());
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
