package io.speclitmus.acceptance;

import io.speclitmus.api.ParagraphResponse;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.review.InvalidModelOutputException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates every acceptance rule against the immutable review snapshot or this request's answers. */
@Component
public class AcceptanceEvidenceValidator {

    private static final int MAX_CASES = 8;

    public List<AcceptanceCaseDraft> validate(
            AcceptanceModelOutput output,
            ReviewResponse review,
            List<ConfirmedAnswer> answers) {
        if (output == null || output.cases() == null || output.cases().size() > MAX_CASES) {
            throw new InvalidModelOutputException("Model returned too many or missing acceptance cases");
        }
        if (output.warnings() == null) {
            throw new InvalidModelOutputException("Model returned missing acceptance warnings");
        }
        output.warnings().forEach(warning -> requireText(warning, 600, "warning"));

        Map<String, ParagraphResponse> paragraphs = review.paragraphs().stream()
                .collect(Collectors.toUnmodifiableMap(ParagraphResponse::id, Function.identity()));
        Map<String, ConfirmedAnswer> confirmedAnswers = answers.stream()
                .collect(Collectors.toUnmodifiableMap(ConfirmedAnswer::questionId, Function.identity()));

        for (int index = 0; index < output.cases().size(); index++) {
            validateCase(output.cases().get(index), index + 1, paragraphs, confirmedAnswers);
        }
        return output.cases();
    }

    private void validateCase(
            AcceptanceCaseDraft draft,
            int caseNumber,
            Map<String, ParagraphResponse> paragraphs,
            Map<String, ConfirmedAnswer> answers) {
        if (draft == null) {
            throw new InvalidModelOutputException("Model returned a missing acceptance case at position " + caseNumber);
        }
        requireText(draft.title(), 300, "case title");
        requireText(draft.given(), 600, "given");
        requireText(draft.when(), 600, "when");
        requireText(draft.then(), 600, "then");
        if (draft.basis() == null || draft.basis().isEmpty()) {
            throw new InvalidModelOutputException("Model returned an acceptance case without basis");
        }

        for (AcceptanceBasisDraft basis : draft.basis()) {
            validateBasis(basis, paragraphs, answers);
        }
    }

    private void validateBasis(
            AcceptanceBasisDraft basis,
            Map<String, ParagraphResponse> paragraphs,
            Map<String, ConfirmedAnswer> answers) {
        if (basis == null || basis.sourceType() == null) {
            throw new InvalidModelOutputException("Model returned an invalid acceptance basis");
        }
        requireText(basis.sourceId(), 40, "basis source ID");
        requireText(basis.quote(), 2_000, "basis quote");

        String sourceText;
        if (basis.sourceType() == EvidenceSourceType.ORIGINAL) {
            ParagraphResponse paragraph = paragraphs.get(basis.sourceId());
            if (paragraph == null) {
                throw new InvalidModelOutputException("Model basis referenced an unknown paragraph");
            }
            sourceText = paragraph.text();
        } else {
            ConfirmedAnswer answer = answers.get(basis.sourceId());
            if (answer == null) {
                throw new InvalidModelOutputException("Model basis referenced an unconfirmed answer");
            }
            sourceText = answer.text();
        }

        if (!sourceText.contains(basis.quote())) {
            throw new InvalidModelOutputException("Model basis quote was not found in its source");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidModelOutputException("Model returned an invalid " + field);
        }
    }
}
