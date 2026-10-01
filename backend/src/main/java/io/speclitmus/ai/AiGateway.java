package io.speclitmus.ai;

import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.review.ReviewQuestionDraft;

import java.util.List;

public interface AiGateway {

    List<ReviewQuestionDraft> review(RequirementSnapshot snapshot);

    String modelName();
}
