package io.speclitmus.requirement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
