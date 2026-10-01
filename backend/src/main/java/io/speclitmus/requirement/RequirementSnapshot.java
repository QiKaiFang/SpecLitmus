package io.speclitmus.requirement;

import java.util.List;

public record RequirementSnapshot(String requirementText, String requirementHash,
                                  List<ParagraphSnapshot> paragraphs) {
    public RequirementSnapshot {
        paragraphs = List.copyOf(paragraphs);
    }
}
