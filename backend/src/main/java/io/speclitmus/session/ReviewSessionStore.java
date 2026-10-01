package io.speclitmus.session;

import io.speclitmus.api.ReviewResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ReviewSessionStore {

    static final int MAX_SESSIONS = 20;

    private final Map<String, ReviewSession> sessions = new LinkedHashMap<>();

    public synchronized void save(ReviewResponse review) {
        sessions.put(review.reviewId(), new ReviewSession(review.reviewId(), review, Instant.now()));
        while (sessions.size() > MAX_SESSIONS) {
            String oldestReviewId = sessions.keySet().iterator().next();
            sessions.remove(oldestReviewId);
        }
    }

    public synchronized Optional<ReviewSession> find(String reviewId) {
        return Optional.ofNullable(sessions.get(reviewId));
    }
}
