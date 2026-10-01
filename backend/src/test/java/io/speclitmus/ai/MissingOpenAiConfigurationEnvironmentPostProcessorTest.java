package io.speclitmus.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissingOpenAiConfigurationEnvironmentPostProcessorTest {

    private final MissingOpenAiConfigurationEnvironmentPostProcessor processor =
            new MissingOpenAiConfigurationEnvironmentPostProcessor();

    @Test
    void disablesTheProviderForStubModeWithoutAKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("speclitmus.ai.mode", "stub");

        processor.postProcessEnvironment(environment, null);

        assertEquals("none", environment.getProperty("spring.ai.model.chat"));
    }

    @Test
    void disablesOpenAiForRealModeWhenTheKeyIsMissingRegardlessOfCase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("speclitmus.ai.mode", "REAL")
                .withProperty("spring.ai.model.chat", "OPENAI");

        processor.postProcessEnvironment(environment, null);

        assertEquals("none", environment.getProperty("spring.ai.model.chat"));
    }

    @Test
    void preservesOpenAiWhenAChatSpecificKeyIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("speclitmus.ai.mode", "real")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.openai.chat.api-key", "test-key");

        processor.postProcessEnvironment(environment, null);

        assertEquals("openai", environment.getProperty("spring.ai.model.chat"));
    }
}
