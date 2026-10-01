package io.speclitmus.review;

import java.util.List;

public record ReviewQuestionDraft(QuestionType type, String paragraphId, String quote, String scenario,
                                  String question, List<String> possibleInterpretations) {
    public ReviewQuestionDraft {
        possibleInterpretations = possibleInterpretations == null ? null : List.copyOf(possibleInterpretations);
    }
}
