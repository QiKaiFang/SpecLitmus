package io.speclitmus.ai;

import io.speclitmus.acceptance.AcceptanceModelOutput;
import io.speclitmus.acceptance.ConfirmedAnswer;
import io.speclitmus.api.ReviewResponse;

import java.util.List;

public interface AcceptanceAiGateway {

    AcceptanceModelOutput acceptance(ReviewResponse review, List<ConfirmedAnswer> answers);

    AcceptanceModelOutput repairAcceptance(
            ReviewResponse review,
            List<ConfirmedAnswer> answers,
            String validationError);

    String modelName();
}
