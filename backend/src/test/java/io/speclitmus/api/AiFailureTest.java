package io.speclitmus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.speclitmus.ai.StubAiGateway;
import io.speclitmus.ai.AiGateway;
import io.speclitmus.SpecLitmusApplication;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.review.ReviewService;
import io.speclitmus.session.ReviewSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiFailureTest {

    private static final String REQUIREMENT = "允许用户修改收货地址。";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unavailableFixtureScenarioReturnsTheStandard503Error() throws Exception {
        MvcResult result = postToScenario("unavailable")
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        assertError(result, "AI_UNAVAILABLE");
    }

    @Test
    void timeoutFixtureScenarioReturnsTheStandard504Error() throws Exception {
        MvcResult result = postToScenario("timeout")
                .andExpect(status().isGatewayTimeout())
                .andReturn();

        assertError(result, "AI_TIMEOUT");
    }

    @Test
    void missingOpenAiKeyStartsAndReturnsARequestLevel503() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SpecLitmusApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("acceptance")
                .properties(
                        "speclitmus.ai.mode=real",
                        "spring.ai.model.chat=openai",
                        "spring.main.banner-mode=off")
                .run(
                        "--speclitmus.ai.mode=real",
                        "--spring.ai.model.chat=openai",
                        "--spring.ai.openai.api-key=",
                        "--spring.ai.openai.chat.api-key=")) {
            ReviewService reviewService = context.getBean(ReviewService.class);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                    .setControllerAdvice(context.getBean(ReviewExceptionHandler.class))
                    .build();

            MvcResult result = mockMvc.perform(post("/api/reviews")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(new ReviewRequest(REQUIREMENT))))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            assertError(result, "AI_CONFIGURATION_MISSING");
        }
    }

    @Test
    void stubModeStartsWithoutAKeyOrAnInitializedProvider() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SpecLitmusApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("acceptance")
                .properties("spring.main.banner-mode=off")
                .run(
                        "--speclitmus.ai.mode=stub",
                        "--spring.ai.model.chat=",
                        "--spring.ai.openai.api-key=",
                        "--spring.ai.openai.chat.api-key=")) {
            assertInstanceOf(StubAiGateway.class, context.getBean(AiGateway.class));
        }
    }

    private org.springframework.test.web.servlet.ResultActions postToScenario(String scenario) throws Exception {
        ReviewService reviewService = new ReviewService(
                new RequirementParser(),
                new StubAiGateway(scenario),
                new ReviewSessionStore(),
                "review-v2");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new ReviewExceptionHandler())
                .build();
        return mockMvc.perform(post("/api/reviews")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new ReviewRequest(REQUIREMENT))));
    }

    private void assertError(MvcResult result, String expectedCode) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(expectedCode, body.path("code").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertEquals(true, body.path("retryable").asBoolean());
    }
}
