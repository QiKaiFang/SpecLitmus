package io.speclitmus.requirement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementParserTest {

    private final RequirementParser parser = new RequirementParser();

    @Test
    void createsStableParagraphSnapshotForR1() {
        RequirementSnapshot snapshot = parser.parse("用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。");

        assertEquals(2, snapshot.paragraphs().size());
        assertEquals(new ParagraphSnapshot("P001", "用户每天可以领取一次优惠券。", 0, 14), snapshot.paragraphs().get(0));
        assertEquals(new ParagraphSnapshot("P002", "新用户可以额外领取一张。", 16, 28), snapshot.paragraphs().get(1));
        assertEquals(64, snapshot.requirementHash().length());
    }

    @Test
    void normalizesCrLfWithoutChangingParagraphText() {
        RequirementSnapshot snapshot = parser.parse("第一段。\r\n\r\n第二段。");

        assertEquals("第一段。\n\n第二段。", snapshot.requirementText());
        assertFalse(snapshot.paragraphs().isEmpty());
        assertEquals(6, snapshot.paragraphs().get(1).start());
    }

    @Test
    void producesTheSameSnapshotForLfAndCrLfInput() {
        RequirementSnapshot lf = parser.parse("用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。");
        RequirementSnapshot crLf = parser.parse("用户每天可以领取一次优惠券。\r\n\r\n新用户可以额外领取一张。");

        assertEquals(lf, crLf);
        assertEquals(lf.requirementText(), crLf.requirementText());
        assertEquals(lf.requirementHash(), crLf.requirementHash());
    }

    @Test
    void keepsNonBlankLineWhitespaceAndUsesSourceSlicesForParagraphs() {
        RequirementSnapshot snapshot = parser.parse("\n\n第一行\n第二行\n\n  保留缩进。\n\n\t\n");

        assertEquals(2, snapshot.paragraphs().size());
        assertEquals(new ParagraphSnapshot("P001", "第一行\n第二行", 2, 9), snapshot.paragraphs().getFirst());
        assertEquals(new ParagraphSnapshot("P002", "  保留缩进。", 11, 18), snapshot.paragraphs().get(1));
        snapshot.paragraphs().forEach(paragraph -> assertEquals(
                paragraph.text(), snapshot.requirementText().substring(paragraph.start(), paragraph.end())));
    }

    @Test
    void treatsARequirementWithoutBlankLinesAsOneParagraph() {
        RequirementSnapshot snapshot = parser.parse("第一行\n第二行");

        assertEquals(1, snapshot.paragraphs().size());
        assertEquals("第一行\n第二行", snapshot.paragraphs().getFirst().text());
        assertTrue(snapshot.paragraphs().getFirst().end() > snapshot.paragraphs().getFirst().start());
    }
}
