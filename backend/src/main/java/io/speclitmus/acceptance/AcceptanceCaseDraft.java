package io.speclitmus.acceptance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AcceptanceCaseDraft(
        String title,
        String given,
        String when,
        String then,
        List<AcceptanceBasisDraft> basis) {

    public AcceptanceCaseDraft {
        basis = basis == null ? null : Collections.unmodifiableList(new ArrayList<>(basis));
    }
}
