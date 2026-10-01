package io.speclitmus.api;

import io.speclitmus.review.ReviewService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReviewResponse createReview(@RequestBody(required = false) ReviewRequest request) {
        if (request == null) {
            throw new ReviewRequestException("请求体不能为空。");
        }
        return reviewService.review(request.requirementText());
    }
}
