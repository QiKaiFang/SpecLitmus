package io.speclitmus.review;

/** A model question whose cited text has been found in the immutable requirement snapshot. */
public record ValidatedReviewQuestion(ReviewQuestionDraft draft, int quoteStart, int quoteEnd) {
}
