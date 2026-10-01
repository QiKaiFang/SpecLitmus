package io.speclitmus.ai;

import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.review.QuestionType;
import io.speclitmus.review.ReviewQuestionDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("acceptance")
@ConditionalOnProperty(prefix = "speclitmus.ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubAiGateway implements AiGateway {

    private static final String REVIEW_VALID_REQUIREMENT = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";

    private final String scenario;

    public StubAiGateway(@Value("${APP_TEST_SCENARIO:${speclitmus.test.scenario:review-valid}}") String scenario) {
        this.scenario = scenario;
    }

    @Override
    public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
        return switch (scenario) {
            case "review-valid" -> reviewValid(snapshot);
            case "review-empty" -> List.of();
            case "unavailable" -> throw new AiModelUnavailableException();
            case "timeout" -> throw new AiModelTimeoutException(new RuntimeException("fixture timeout"));
            default -> throw new AiModelUnavailableException();
        };
    }

    @Override
    public List<ReviewQuestionDraft> repairReview(RequirementSnapshot snapshot, String validationError) {
        return review(snapshot);
    }

    @Override
    public String modelName() {
        return "fixture";
    }

    private List<ReviewQuestionDraft> reviewValid(RequirementSnapshot snapshot) {
        if (!REVIEW_VALID_REQUIREMENT.equals(snapshot.requirementText())) {
            throw new StubInputUnsupportedException();
        }
        return List.of(
                new ReviewQuestionDraft(
                        QuestionType.AMBIGUITY,
                        "P001",
                        "每天",
                        "23:59 领取后，次日 00:01 再次领取。",
                        "每天按自然日还是间隔 24 小时计算？",
                        List.of("自然日", "滚动 24 小时")
                ),
                new ReviewQuestionDraft(
                        QuestionType.AMBIGUITY,
                        "P002",
                        "新用户",
                        "注册三天但从未领券的用户首次领取。",
                        "新用户如何定义？",
                        List.of()
                )
        );
    }
}
