package io.speclitmus.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Prevent Spring AI from failing application startup when real mode has no API key.
 * The review gateway then reports the missing configuration as a request-level 503.
 */
public class MissingOpenAiConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "speclitmusMissingOpenAiConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("speclitmus.ai.mode", "real");
        if ("stub".equalsIgnoreCase(mode)) {
            disableChatModel(environment);
            return;
        }

        String chatModel = environment.getProperty("spring.ai.model.chat");
        if (!"real".equalsIgnoreCase(mode)
                || (StringUtils.hasText(chatModel) && !"openai".equalsIgnoreCase(chatModel))
                || hasApiKey(environment)) {
            return;
        }

        disableChatModel(environment);
    }

    private void disableChatModel(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME, Map.of("spring.ai.model.chat", "none")));
    }

    private boolean hasApiKey(ConfigurableEnvironment environment) {
        return StringUtils.hasText(environment.getProperty("spring.ai.openai.chat.api-key"))
                || StringUtils.hasText(environment.getProperty("spring.ai.openai.api-key"));
    }
}
