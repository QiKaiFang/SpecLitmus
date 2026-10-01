package io.speclitmus.ai;

public class AiModelUnavailableException extends RuntimeException {

    public AiModelUnavailableException() {
        super("The configured text model is unavailable");
    }

    public AiModelUnavailableException(Throwable cause) {
        super("The configured text model is unavailable", cause);
    }
}
