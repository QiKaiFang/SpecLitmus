package io.speclitmus.review;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record ReviewQuestionDraft(QuestionType type, String paragraphId, String quote, String scenario,
                                  String question, List<String> possibleInterpretations) {
    public ReviewQuestionDraft {
        // Keep a malformed null item available for EvidenceValidator to turn into
        // the standard AI_OUTPUT_INVALID response instead of leaking a constructor NPE.
        possibleInterpretations = possibleInterpretations == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(possibleInterpretations));
    }
}
