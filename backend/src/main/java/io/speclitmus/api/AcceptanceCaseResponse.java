package io.speclitmus.api;

import java.util.List;

public record AcceptanceCaseResponse(
        String caseId,
        String title,
        String given,
        String when,
        String then,
        List<AcceptanceBasisResponse> basis) {

    public AcceptanceCaseResponse {
        basis = List.copyOf(basis);
    }
}
