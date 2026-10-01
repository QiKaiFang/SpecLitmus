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
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerS2IntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsBlankInputAndMissingRequirementTextWithTheStandardBadRequestError() throws Exception {
        MvcResult blankResult = postReview(" \t\n")
                .andExpect(status().isBadRequest())
                .andReturn();
        assertError(blankResult, "INVALID_REQUEST", false);

        MvcResult missingFieldResult = reviewEmptyMockMvc().perform(post("/api/reviews")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertError(missingFieldResult, "INVALID_REQUEST", false);

        MvcResult malformedBodyResult = reviewEmptyMockMvc().perform(post("/api/reviews")
                        .contentType(APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertError(malformedBodyResult, "INVALID_REQUEST", false);
    }

    @Test
    void rejectsTwoThousandAndOneCharactersButAcceptsTheTwoThousandCharacterBoundary() throws Exception {
        MvcResult overLimitResult = postReview("测".repeat(2_001))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertError(overLimitResult, "INVALID_REQUEST", false);

        MvcResult boundaryResult = postReview("测".repeat(2_000))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(boundaryResult.getResponse().getContentAsByteArray());
        assertFalse(response.path("reviewId").asText().isBlank());
        assertTrue(response.path("questions").isArray());
        assertEquals(0, response.path("questions").size());
        assertEquals("fixture", response.path("metadata").path("model").asText());
    }

    @Test
    void returnsAStandard415ErrorForANonJsonRequest() throws Exception {
        MvcResult result = reviewEmptyMockMvc().perform(post("/api/reviews")
                        .contentType(TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();

        assertError(result, "UNSUPPORTED_MEDIA_TYPE", false);
    }

    private org.springframework.test.web.servlet.ResultActions postReview(String requirementText) throws Exception {
        return reviewEmptyMockMvc().perform(post("/api/reviews")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new ReviewRequest(requirementText))));
    }

    private MockMvc reviewEmptyMockMvc() {
        ReviewService reviewService = new ReviewService(
                new RequirementParser(),
                new StubAiGateway("review-empty"),
                new ReviewSessionStore(),
                "review-v2");
        return MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new ReviewExceptionHandler())
                .build();
    }

    private void assertError(MvcResult result, String expectedCode, boolean expectedRetryable) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(expectedCode, body.path("code").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertEquals(expectedRetryable, body.path("retryable").asBoolean());
    }
}
