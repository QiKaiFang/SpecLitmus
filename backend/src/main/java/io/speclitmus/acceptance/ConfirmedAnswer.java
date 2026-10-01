package io.speclitmus.acceptance;

/** A non-blank answer explicitly confirmed by the user for the current review. */
public record ConfirmedAnswer(String questionId, String text) {
}
