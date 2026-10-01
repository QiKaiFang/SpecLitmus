package io.speclitmus.api;

import java.util.List;

public record ReviewResponse(
        String reviewId,
        String requirementText,
        String requirementHash,
        List<ParagraphResponse> paragraphs,
        List<ReviewQuestionResponse> questions,
        ReviewMetadata metadata) {

    public ReviewResponse {
        paragraphs = List.copyOf(paragraphs);
        questions = List.copyOf(questions);
    }
}
