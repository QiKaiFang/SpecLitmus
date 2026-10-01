package io.speclitmus.review;

import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.requirement.RequirementSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceValidatorTest {

    private final RequirementParser parser = new RequirementParser();
    private final EvidenceValidator validator = new EvidenceValidator();

    @Test
    void calculatesGlobalUtf16OffsetsForTheFirstMatchingQuote() {
        RequirementSnapshot emojiSnapshot = parser.parse("😀用户每天可以领取一次优惠券。");
        ValidatedReviewQuestion emojiQuestion = validator.validate(
                List.of(draft("P001", "每天")), emojiSnapshot.paragraphs()).getFirst();

        assertEquals(4, emojiQuestion.quoteStart());
        assertEquals(6, emojiQuestion.quoteEnd());
        assertEquals("每天", emojiSnapshot.requirementText()
                .substring(emojiQuestion.quoteStart(), emojiQuestion.quoteEnd()));

        RequirementSnapshot duplicateSnapshot = parser.parse("用户每天领取一次。系统每天重置。");
        ValidatedReviewQuestion duplicateQuestion = validator.validate(
                List.of(draft("P001", "每天")), duplicateSnapshot.paragraphs()).getFirst();

        assertEquals(2, duplicateQuestion.quoteStart());
        assertEquals(4, duplicateQuestion.quoteEnd());
    }

    @Test
    void rejectsUnknownParagraphsAndQuotesOutsideTheClaimedParagraph() {
        RequirementSnapshot snapshot = parser.parse("用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。");

        assertThrows(InvalidModelOutputException.class,
                () -> validator.validate(List.of(draft("P999", "每天")), snapshot.paragraphs()));
        assertThrows(InvalidModelOutputException.class,
                () -> validator.validate(List.of(draft("P001", "新用户")), snapshot.paragraphs()));
    }

    @Test
    void rejectsMalformedCardFieldsAndMoreThanFiveCards() {
        RequirementSnapshot snapshot = parser.parse("用户每天可以领取一次优惠券。");

        assertThrows(InvalidModelOutputException.class,
                () -> validator.validate(List.of(draft("P001", " ")), snapshot.paragraphs()));
        assertThrows(InvalidModelOutputException.class,
                () -> validator.validate(List.of(new ReviewQuestionDraft(
                        QuestionType.AMBIGUITY, "P001", "每天", "场景", "问题", null)), snapshot.paragraphs()));
        assertThrows(InvalidModelOutputException.class,
                () -> validator.validate(List.of(draft("P001", "每天"), draft("P001", "每天"),
                        draft("P001", "每天"), draft("P001", "每天"), draft("P001", "每天"),
                        draft("P001", "每天")), snapshot.paragraphs()));
    }

    private ReviewQuestionDraft draft(String paragraphId, String quote) {
        return new ReviewQuestionDraft(QuestionType.AMBIGUITY, paragraphId, quote,
                "用户再次领取优惠券时。", "每天的计算规则是什么？", List.of());
    }
}
