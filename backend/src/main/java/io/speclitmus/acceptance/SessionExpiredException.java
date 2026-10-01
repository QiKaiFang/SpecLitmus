package io.speclitmus.acceptance;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException() {
        super("Review session is no longer available");
    }
}
