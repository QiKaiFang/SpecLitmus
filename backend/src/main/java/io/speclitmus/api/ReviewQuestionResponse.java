package io.speclitmus.api;

import io.speclitmus.review.QuestionType;

import java.util.List;

public record ReviewQuestionResponse(
        String questionId,
        QuestionType type,
        String paragraphId,
        String quote,
        Integer quoteStart,
        Integer quoteEnd,
        String scenario,
        String question,
        List<String> possibleInterpretations) {

    public ReviewQuestionResponse {
        possibleInterpretations = List.copyOf(possibleInterpretations);
    }
}
