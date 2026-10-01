package io.speclitmus.session;

import io.speclitmus.api.ReviewResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ReviewSessionStore {

    private final ConcurrentMap<String, ReviewSession> sessions = new ConcurrentHashMap<>();

    public void save(ReviewResponse review) {
        sessions.put(review.reviewId(), new ReviewSession(review.reviewId(), review, Instant.now()));
    }

    public Optional<ReviewSession> find(String reviewId) {
        return Optional.ofNullable(sessions.get(reviewId));
    }
}
