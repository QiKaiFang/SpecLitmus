package io.speclitmus.review;

import io.speclitmus.requirement.ParagraphSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Verifies that review cards are structurally usable and that every citation is a
 * contiguous excerpt of the paragraph identified by the model.
 */
@Component
public class EvidenceValidator {

    private static final int MAX_QUESTIONS = 5;

    public List<ValidatedReviewQuestion> validate(
            List<ReviewQuestionDraft> drafts,
            List<ParagraphSnapshot> paragraphs) {
        if (drafts == null || drafts.size() > MAX_QUESTIONS) {
            throw new InvalidModelOutputException("Model returned too many or missing review questions");
        }
        if (paragraphs == null) {
            throw new InvalidModelOutputException("Requirement snapshot did not contain paragraphs");
        }

        Map<String, ParagraphSnapshot> paragraphsById = paragraphs.stream()
                .collect(Collectors.toUnmodifiableMap(ParagraphSnapshot::id, Function.identity()));

        return IntStream.range(0, drafts.size())
                .mapToObj(index -> validateDraft(drafts.get(index), index + 1, paragraphsById))
                .toList();
    }

    private ValidatedReviewQuestion validateDraft(
            ReviewQuestionDraft draft,
            int questionNumber,
            Map<String, ParagraphSnapshot> paragraphsById) {
        if (draft == null) {
            throw new InvalidModelOutputException(
                    "Model returned a missing review question at position " + questionNumber);
        }
        if (draft.type() == null) {
            throw new InvalidModelOutputException(
                    "Model returned a review question without a type at position " + questionNumber);
        }
        requireText(draft.paragraphId(), 20, "paragraph ID");
        requireText(draft.quote(), 200, "quote");
        requireText(draft.scenario(), 500, "scenario");
        requireText(draft.question(), 300, "question");
        validatePossibleInterpretations(draft.possibleInterpretations());

        ParagraphSnapshot paragraph = paragraphsById.get(draft.paragraphId());
        if (paragraph == null) {
            throw new InvalidModelOutputException(
                    "Model returned a review question with an unknown paragraph ID at position " + questionNumber);
        }

        int paragraphQuoteStart = paragraph.text().indexOf(draft.quote());
        if (paragraphQuoteStart < 0) {
            throw new InvalidModelOutputException(
                    "Model quote was not found in its paragraph at position " + questionNumber);
        }
        int quoteStart = paragraph.start() + paragraphQuoteStart;
        int quoteEnd = quoteStart + draft.quote().length();
        return new ValidatedReviewQuestion(draft, quoteStart, quoteEnd);
    }

    private void validatePossibleInterpretations(List<String> interpretations) {
        if (interpretations == null || interpretations.size() > 3) {
            throw new InvalidModelOutputException("Model returned invalid possible interpretations");
        }
        interpretations.forEach(value -> requireText(value, 200, "possible interpretation"));
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidModelOutputException("Model returned an invalid " + field);
        }
    }
}
