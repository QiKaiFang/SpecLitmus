package io.speclitmus.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.review.ReviewModelOutput;
import io.speclitmus.review.ReviewQuestionDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "speclitmus.ai", name = "mode", havingValue = "real", matchIfMissing = true)
public class OpenAiReviewGateway implements AiGateway {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiReviewGateway.class);

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String model;
    private final String promptVersion;

    public OpenAiReviewGateway(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${speclitmus.ai.model:gpt-4o-mini}") String model,
            @Value("${speclitmus.ai.review-prompt-version:review-v2}") String promptVersion) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.model = model;
        this.promptVersion = promptVersion;
    }

    @Override
    public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
        return generateReview(snapshot, null);
    }

    @Override
    public List<ReviewQuestionDraft> repairReview(RequirementSnapshot snapshot, String validationError) {
        return generateReview(snapshot, validationError);
    }

    private List<ReviewQuestionDraft> generateReview(RequirementSnapshot snapshot, String validationError) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new AiModelUnavailableException();
        }

        String rawResponse;
        try {
            String systemPrompt = readPrompt();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requirementText", snapshot.requirementText());
            payload.put("paragraphs", snapshot.paragraphs());
            if (validationError != null) {
                payload.put("previousOutputError", validationError);
            }
            String userPrompt = objectMapper.writeValueAsString(payload);
            rawResponse = builder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (AiModelUnavailableException | InvalidModelOutputException exception) {
            throw exception;
        } catch (Exception exception) {
            if (isTimeout(exception)) {
                throw new AiModelTimeoutException(exception);
            }
            throw new AiModelUnavailableException(exception);
        }

        try {
            ReviewModelOutput output = objectMapper.readValue(extractReviewJson(rawResponse), ReviewModelOutput.class);
            if (output == null || output.questions() == null) {
                throw new InvalidModelOutputException("Model response did not contain questions");
            }
            return output.questions();
        } catch (JsonProcessingException exception) {
            logJsonFailure(exception);
            throw new InvalidModelOutputException("Model response was not valid review JSON", exception);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    private String readPrompt() {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + promptVersion + ".st");
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InvalidModelOutputException("Review prompt could not be loaded", exception);
        }
    }

    private String extractReviewJson(String response) {
        if (response == null || response.isBlank()) {
            throw new InvalidModelOutputException("Model returned an empty response");
        }
        String value = response.strip();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            int closingFence = value.lastIndexOf("```");
            if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
                throw new InvalidModelOutputException("Model response had an incomplete Markdown fence");
            }
            value = value.substring(firstLineEnd + 1, closingFence).strip();
        }

        try {
            var root = objectMapper.readTree(value);
            if (root != null && root.isObject() && root.has("questions")) {
                return value;
            }
        } catch (JsonProcessingException ignored) {
            // Some OpenAI-compatible models prepend a short explanation. Try to locate the JSON object.
        }

        for (int start = 0; start < value.length(); start++) {
            if (value.charAt(start) != '{') {
                continue;
            }
            int end = findObjectEnd(value, start);
            if (end < 0) {
                continue;
            }
            String candidate = value.substring(start, end + 1);
            try {
                var root = objectMapper.readTree(candidate);
                if (root != null && root.isObject() && root.has("questions")) {
                    return candidate;
                }
            } catch (JsonProcessingException ignored) {
                // Keep looking in case the response contains an example before its final answer.
            }
        }
        throw new InvalidModelOutputException("Model response did not contain a JSON review object");
    }

    private int findObjectEnd(String value, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private void logJsonFailure(JsonProcessingException exception) {
        var location = exception.getLocation();
        if (location == null) {
            logger.warn("Model response JSON parsing failed ({})", exception.getClass().getSimpleName());
            return;
        }
        logger.warn("Model response JSON parsing failed ({}) at line {}, column {}",
                exception.getClass().getSimpleName(), location.getLineNr(), location.getColumnNr());
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getSimpleName().toLowerCase();
            if (name.contains("timeout") || name.contains("timedout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
