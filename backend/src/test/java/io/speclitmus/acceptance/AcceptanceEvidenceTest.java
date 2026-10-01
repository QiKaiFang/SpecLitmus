package io.speclitmus.acceptance;

import io.speclitmus.api.ParagraphResponse;
import io.speclitmus.api.ReviewMetadata;
import io.speclitmus.api.ReviewQuestionResponse;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.review.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcceptanceEvidenceTest {

    private final AcceptanceEvidenceValidator validator = new AcceptanceEvidenceValidator();
    private final ReviewResponse review = new ReviewResponse(
            "R1",
            "用户每天可以领取一次优惠券。",
            "hash",
            List.of(new ParagraphResponse("P001", "用户每天可以领取一次优惠券。", 0, 14)),
            List.of(new ReviewQuestionResponse(
                    "Q001", QuestionType.AMBIGUITY, "P001", "每天", 2, 4,
                    "跨日领取。", "每天如何计算？", List.of())),
            new ReviewMetadata("fixture", "review-v2"));
    private final List<ConfirmedAnswer> answers = List.of(
            new ConfirmedAnswer("Q001", "按北京时间自然日重置。"));

    @Test
    void acceptsVerbatimOriginalAndConfirmedAnswerEvidence() {
        AcceptanceModelOutput output = outputWithBasis(List.of(
                new AcceptanceBasisDraft(EvidenceSourceType.ORIGINAL, "P001", "每天可以领取一次"),
                new AcceptanceBasisDraft(EvidenceSourceType.ANSWER, "Q001", "北京时间自然日")));

        assertDoesNotThrow(() -> validator.validate(output, review, answers));
    }

    @Test
    void rejectsUnknownOrUnconfirmedEvidenceSources() {
        assertThrows(InvalidModelOutputException.class, () -> validator.validate(
                outputWithBasis(List.of(new AcceptanceBasisDraft(
                        EvidenceSourceType.ORIGINAL, "P999", "每天"))), review, answers));
        assertThrows(InvalidModelOutputException.class, () -> validator.validate(
                outputWithBasis(List.of(new AcceptanceBasisDraft(
                        EvidenceSourceType.ANSWER, "Q002", "自然日"))), review, answers));
    }

    @Test
    void rejectsInventedQuotesAndMoreThanEightCases() {
        assertThrows(InvalidModelOutputException.class, () -> validator.validate(
                outputWithBasis(List.of(new AcceptanceBasisDraft(
                        EvidenceSourceType.ANSWER, "Q001", "滚动 24 小时"))), review, answers));

        AcceptanceCaseDraft valid = outputWithBasis(List.of(new AcceptanceBasisDraft(
                EvidenceSourceType.ORIGINAL, "P001", "每天"))).cases().getFirst();
        assertThrows(InvalidModelOutputException.class, () -> validator.validate(
                new AcceptanceModelOutput(java.util.Collections.nCopies(9, valid), List.of()), review, answers));
    }

    private AcceptanceModelOutput outputWithBasis(List<AcceptanceBasisDraft> basis) {
        return new AcceptanceModelOutput(List.of(new AcceptanceCaseDraft(
                "自然日边界",
                "用户当天已领取。",
                "用户次日再次领取。",
                "系统按已确认规则处理。",
                basis)), List.of());
    }
}
