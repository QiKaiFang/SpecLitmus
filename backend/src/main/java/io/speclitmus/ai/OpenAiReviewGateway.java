package io.speclitmus.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.review.ReviewModelOutput;
import io.speclitmus.review.ReviewQuestionDraft;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "speclitmus.ai", name = "mode", havingValue = "real", matchIfMissing = true)
public class OpenAiReviewGateway implements AiGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String model;

    public OpenAiReviewGateway(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${speclitmus.ai.model:gpt-4o-mini}") String model) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.model = model;
    }

    @Override
    public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new AiModelUnavailableException();
        }

        String rawResponse;
        try {
            String systemPrompt = readPrompt();
            String userPrompt = objectMapper.writeValueAsString(Map.of(
                    "requirementText", snapshot.requirementText(),
                    "paragraphs", snapshot.paragraphs()
            ));
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
            ReviewModelOutput output = objectMapper.readValue(stripMarkdownFence(rawResponse), ReviewModelOutput.class);
            if (output == null || output.questions() == null) {
                throw new InvalidModelOutputException("Model response did not contain questions");
            }
            return output.questions();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidModelOutputException("Model response was not valid review JSON", exception);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    private String readPrompt() {
        Resource resource = resourceLoader.getResource("classpath:prompts/review-v1.st");
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InvalidModelOutputException("Review prompt could not be loaded", exception);
        }
    }

    private String stripMarkdownFence(String response) {
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
        return value;
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
