package io.speclitmus.acceptance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AcceptanceModelOutput(List<AcceptanceCaseDraft> cases, List<String> warnings) {

    public AcceptanceModelOutput {
        cases = cases == null ? null : Collections.unmodifiableList(new ArrayList<>(cases));
        warnings = warnings == null ? null : Collections.unmodifiableList(new ArrayList<>(warnings));
    }
}
