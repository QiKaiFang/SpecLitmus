package io.speclitmus.api;

import java.util.List;

public record AcceptanceResponse(
        List<AcceptanceCaseResponse> cases,
        List<String> pendingQuestionIds,
        List<String> warnings,
        ReviewMetadata metadata) {

    public AcceptanceResponse {
        cases = List.copyOf(cases);
        pendingQuestionIds = List.copyOf(pendingQuestionIds);
        warnings = List.copyOf(warnings);
    }
}
