package io.speclitmus.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.review.ReviewService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OpenAiReviewGatewayProtocolTest {

    private static final String R1 = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";
    private static final String MODEL_OUTPUT = """
            {"questions":[{"type":"AMBIGUITY","paragraphId":"P001","quote":"每天","scenario":"在当天领券后跨到次日再次领取。","question":"每天按自然日还是间隔 24 小时计算？","possibleInterpretations":["自然日","滚动 24 小时"]}]}
            """;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<String> REQUEST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_BODY = new AtomicReference<>();
    private static final AtomicBoolean DELAY_NEXT_RESPONSE = new AtomicBoolean();
    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
    private static final HttpServer MOCK_SERVER = startMockServer();

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetRequestEvidence() {
        REQUEST_PATH.set(null);
        REQUEST_AUTHORIZATION.set(null);
        REQUEST_BODY.set(null);
        REQUEST_COUNT.set(0);
        DELAY_NEXT_RESPONSE.set(false);
    }

    @DynamicPropertySource
    static void configureOpenAiEndpoint(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", () -> "integration-test-key");
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + MOCK_SERVER.getAddress().getPort());
        registry.add("spring.ai.openai.chat.options.model", () -> "protocol-test-model");
        registry.add("spring.http.client.read-timeout", () -> "200ms");
        registry.add("speclitmus.ai.model", () -> "protocol-test-model");
    }

    @AfterAll
    static void stopMockServer() {
        MOCK_SERVER.stop(0);
    }

    @Test
    void sendsOpenAiChatRequestAndParsesTheReviewQuestions() throws Exception {
        ReviewResponse response = reviewService.review(R1);

        assertEquals("protocol-test-model", response.metadata().model());
        assertEquals(1, response.questions().size());
        assertEquals("Q001", response.questions().getFirst().questionId());
        assertEquals("P001", response.questions().getFirst().paragraphId());
        assertEquals("每天", response.questions().getFirst().quote());

        assertEquals("/v1/chat/completions", REQUEST_PATH.get());
        assertEquals("Bearer integration-test-key", REQUEST_AUTHORIZATION.get());
        JsonNode request = objectMapper.readTree(REQUEST_BODY.get());
        assertEquals("protocol-test-model", request.path("model").asText());
        assertEquals(2, request.path("messages").size());
        assertTrue(request.path("messages").get(1).path("content").asText()
                .contains("用户每天可以领取一次优惠券。"));
        assertEquals(1, REQUEST_COUNT.get());
    }

    @Test
    void mapsTheConfiguredHttpReadTimeoutToATimeoutFailure() {
        DELAY_NEXT_RESPONSE.set(true);

        assertThrows(AiModelTimeoutException.class, () -> reviewService.review(R1));
        assertEquals(1, REQUEST_COUNT.get());
    }

    private static HttpServer startMockServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", OpenAiReviewGatewayProtocolTest::respondToChatCompletion);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondToChatCompletion(HttpExchange exchange) throws IOException {
        REQUEST_COUNT.incrementAndGet();
        REQUEST_PATH.set(exchange.getRequestURI().getPath());
        REQUEST_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
        REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        if (DELAY_NEXT_RESPONSE.compareAndSet(true, false)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] responseBody = MAPPER.writeValueAsBytes(Map.of(
                "id", "chatcmpl-protocol-test",
                "object", "chat.completion",
                "created", 1,
                "model", "protocol-test-model",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", MODEL_OUTPUT),
                        "finish_reason", "stop"
                )),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)
        ));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (var response = exchange.getResponseBody()) {
            response.write(responseBody);
        }
    }
}
