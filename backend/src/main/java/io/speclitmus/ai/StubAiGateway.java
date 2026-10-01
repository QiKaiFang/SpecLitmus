package io.speclitmus.ai;

import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.review.QuestionType;
import io.speclitmus.review.ReviewQuestionDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("acceptance")
@ConditionalOnProperty(prefix = "speclitmus.ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubAiGateway implements AiGateway {

    private static final String REVIEW_VALID_REQUIREMENT = "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。";

    private final String scenario;
    private final AtomicInteger totalCallCount = new AtomicInteger();
    private final ThreadLocal<RequestCalls> requestCalls = new ThreadLocal<>();

    public StubAiGateway(@Value("${APP_TEST_SCENARIO:${speclitmus.test.scenario:review-valid}}") String scenario) {
        this.scenario = scenario;
    }

    @Override
    public List<ReviewQuestionDraft> review(RequirementSnapshot snapshot) {
        // A review() call is the beginning of a new business request. Reset the
        // per-request sequence here rather than carrying state between HTTP calls.
        RequestCalls calls = new RequestCalls();
        requestCalls.set(calls);
        recordCall(calls);

        return switch (scenario) {
            case "review-valid" -> reviewValid(snapshot);
            case "review-empty" -> List.of();
            case "repair-ok", "repair-fail" -> throw invalidFixtureOutput();
            case "bad-paragraph" -> List.of(new ReviewQuestionDraft(
                    QuestionType.AMBIGUITY,
                    "P999",
                    "每天",
                    "用户再次领取优惠券时。",
                    "每天的计算规则是什么？",
                    List.of()));
            case "bad-quote" -> List.of(new ReviewQuestionDraft(
                    QuestionType.AMBIGUITY,
                    "P001",
                    "未出现在原文中的引用",
                    "用户再次领取优惠券时。",
                    "每天的计算规则是什么？",
                    List.of()));
            case "unavailable" -> throw new AiModelUnavailableException();
            case "timeout" -> throw new AiModelTimeoutException(new RuntimeException("fixture timeout"));
            default -> throw new AiModelUnavailableException();
        };
    }

    @Override
    public List<ReviewQuestionDraft> repairReview(RequirementSnapshot snapshot, String validationError) {
        RequestCalls calls = requestCalls.get();
        if (calls == null) {
            // Keep direct gateway usage deterministic as well as HTTP usage. In
            // normal service flow this branch is never used: repair follows review.
            calls = new RequestCalls();
            requestCalls.set(calls);
        }
        recordCall(calls);

        return switch (scenario) {
            case "review-valid" -> reviewValid(snapshot);
            case "review-empty" -> List.of();
            case "repair-ok" -> reviewValid(snapshot);
            case "repair-fail" -> throw invalidFixtureOutput();
            case "bad-paragraph" -> List.of(new ReviewQuestionDraft(
                    QuestionType.AMBIGUITY,
                    "P999",
                    "每天",
                    "用户再次领取优惠券时。",
                    "每天的计算规则是什么？",
                    List.of()));
            case "bad-quote" -> List.of(new ReviewQuestionDraft(
                    QuestionType.AMBIGUITY,
                    "P001",
                    "未出现在原文中的引用",
                    "用户再次领取优惠券时。",
                    "每天的计算规则是什么？",
                    List.of()));
            default -> throw new IllegalStateException("Unexpected fixture repair for scenario: " + scenario);
        };
    }

    @Override
    public String modelName() {
        return "fixture";
    }

    /** Number of model calls made for the current business request on this thread. */
    public int currentRequestCallCount() {
        RequestCalls calls = requestCalls.get();
        return calls == null ? 0 : calls.count;
    }

    /** Total model calls made by this fixture instance, across its requests. */
    public int totalCallCount() {
        return totalCallCount.get();
    }

    private void recordCall(RequestCalls calls) {
        calls.count++;
        totalCallCount.incrementAndGet();
    }

    private InvalidModelOutputException invalidFixtureOutput() {
        return new InvalidModelOutputException("固定模型返回了不可解析的审查 JSON。");
    }

    private static final class RequestCalls {
        private int count;
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
