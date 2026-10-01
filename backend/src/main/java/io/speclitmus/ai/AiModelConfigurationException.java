package io.speclitmus.ai;

/** Raised when the configured model cannot be initialized from local settings. */
public class AiModelConfigurationException extends RuntimeException {

    public AiModelConfigurationException() {
        super("The configured text model is missing required configuration");
    }

    public AiModelConfigurationException(Throwable cause) {
        super("The configured text model is missing required configuration", cause);
    }
}
