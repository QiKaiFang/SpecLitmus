package io.speclitmus.session;

import io.speclitmus.api.ReviewMetadata;
import io.speclitmus.api.ReviewResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSessionStoreTest {

    @Test
    void keepsOnlyTheTwentyMostRecentReviews() {
        ReviewSessionStore store = new ReviewSessionStore();

        for (int index = 1; index <= 21; index++) {
            store.save(review("review-" + index));
        }

        assertThat(store.find("review-1")).isEmpty();
        assertThat(store.find("review-2")).isPresent();
        assertThat(store.find("review-21")).isPresent();
    }

    @Test
    void replacingAnExistingReviewDoesNotEvictAnotherSession() {
        ReviewSessionStore store = new ReviewSessionStore();
        for (int index = 1; index <= 20; index++) {
            store.save(review("review-" + index));
        }

        store.save(review("review-10"));

        assertThat(store.find("review-1")).isPresent();
        assertThat(store.find("review-20")).isPresent();
    }

    private ReviewResponse review(String id) {
        return new ReviewResponse(
                id,
                "需求",
                "hash-" + id,
                List.of(),
                List.of(),
                new ReviewMetadata("fixture", "review-v2"));
    }
}
