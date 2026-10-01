package io.speclitmus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"speclitmus.ai.mode=stub", "spring.ai.model.chat=none"})
@AutoConfigureMockMvc
@ActiveProfiles("acceptance")
class ReviewControllerIntegrationTest {

    private static final String R1 = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewSessionStore sessionStore;

    @Test
    void returnsCompleteReviewCardsAndStoresTheSession() throws Exception {
        byte[] requestBody = objectMapper.writeValueAsBytes(new ReviewRequest(R1));

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertFalse(response.path("reviewId").asText().isBlank());
        assertEquals(R1, response.path("requirementText").asText());
        assertEquals(2, response.path("paragraphs").size());
        assertEquals(2, response.path("questions").size());
        assertEquals("fixture", response.path("metadata").path("model").asText());
        assertEquals("review-v2", response.path("metadata").path("promptVersion").asText());

        Set<String> questionIds = new HashSet<>();
        for (JsonNode question : response.path("questions")) {
            assertFalse(question.path("questionId").asText().isBlank());
            assertTrue(questionIds.add(question.path("questionId").asText()));
            assertFalse(question.path("type").asText().isBlank());
            assertFalse(question.path("paragraphId").asText().isBlank());
            assertFalse(question.path("quote").asText().isBlank());
            assertFalse(question.path("scenario").asText().isBlank());
            assertFalse(question.path("question").asText().isBlank());
            assertTrue(question.path("possibleInterpretations").isArray());
        }

        assertTrue(sessionStore.find(response.path("reviewId").asText()).isPresent());
        assertNotNull(sessionStore.find(response.path("reviewId").asText()).orElseThrow().createdAt());
        assertEquals("Q001", response.path("questions").get(0).path("questionId").asText());
        assertEquals("Q002", response.path("questions").get(1).path("questionId").asText());
    }

    @Test
    void explainsWhenCustomInputIsSubmittedToTheFixedTestGateway() throws Exception {
        byte[] requestBody = objectMapper.writeValueAsBytes(new ReviewRequest("允许用户修改收货地址。"));

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals("FIXTURE_INPUT_UNSUPPORTED", response.path("code").asText());
        assertTrue(response.path("message").asText().contains("真实模型模式"));
        assertFalse(response.path("retryable").asBoolean());
        assertFalse(response.has("reviewId"));
    }
}
