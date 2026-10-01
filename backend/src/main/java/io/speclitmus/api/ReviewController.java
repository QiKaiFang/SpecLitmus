package io.speclitmus.api;

import io.speclitmus.acceptance.AcceptanceService;
import io.speclitmus.review.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReviewController {

    private final ReviewService reviewService;
    private final AcceptanceService acceptanceService;

    /** Kept for focused standalone controller tests covering only review creation. */
    public ReviewController(ReviewService reviewService) {
        this(reviewService, null);
    }

    @Autowired
    public ReviewController(ReviewService reviewService, AcceptanceService acceptanceService) {
        this.reviewService = reviewService;
        this.acceptanceService = acceptanceService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReviewResponse createReview(@RequestBody(required = false) ReviewRequest request) {
        if (request == null) {
            throw new ReviewRequestException("请求体不能为空。");
        }
        return reviewService.review(request.requirementText());
    }

    @PostMapping(path = "/{reviewId}/acceptance", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AcceptanceResponse createAcceptance(
            @PathVariable String reviewId,
            @RequestBody(required = false) AcceptanceRequest request) {
        if (request == null) {
            throw new ReviewRequestException("请求体不能为空。");
        }
        return acceptanceService.generate(reviewId, request);
    }
}
