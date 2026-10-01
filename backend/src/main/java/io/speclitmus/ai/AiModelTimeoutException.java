package io.speclitmus.ai;

public class AiModelTimeoutException extends RuntimeException {

    public AiModelTimeoutException(Throwable cause) {
        super("The configured text model timed out", cause);
    }
}
