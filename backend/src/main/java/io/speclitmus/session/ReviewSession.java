package io.speclitmus.session;

import io.speclitmus.api.ReviewResponse;

import java.time.Instant;

public record ReviewSession(String reviewId, ReviewResponse review, Instant createdAt) {
}
