package io.speclitmus.api;

public record ApiError(String code, String message, boolean retryable) {
}
