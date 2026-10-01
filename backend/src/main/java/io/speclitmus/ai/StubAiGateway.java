package io.speclitmus.ai;

import io.speclitmus.acceptance.AcceptanceBasisDraft;
import io.speclitmus.acceptance.AcceptanceCaseDraft;
import io.speclitmus.acceptance.AcceptanceModelOutput;
import io.speclitmus.acceptance.ConfirmedAnswer;
import io.speclitmus.acceptance.EvidenceSourceType;
import io.speclitmus.api.ReviewQuestionResponse;
import io.speclitmus.api.ReviewResponse;
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
public class StubAiGateway implements AiGateway, AcceptanceAiGateway {

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
            case "review-valid", "workflow-partial", "workflow-all", "workflow",
                    "acceptance-bad-basis" -> reviewValid(snapshot);
            case "review-empty", "workflow-empty" -> List.of();
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
            case "review-valid", "workflow-partial", "workflow-all", "workflow",
                    "acceptance-bad-basis" -> reviewValid(snapshot);
            case "review-empty", "workflow-empty" -> List.of();
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

    @Override
    public AcceptanceModelOutput acceptance(ReviewResponse review, List<ConfirmedAnswer> answers) {
        RequestCalls calls = new RequestCalls();
        requestCalls.set(calls);
        recordCall(calls);
        return acceptanceFixture(review, answers);
    }

    @Override
    public AcceptanceModelOutput repairAcceptance(
            ReviewResponse review,
            List<ConfirmedAnswer> answers,
            String validationError) {
        RequestCalls calls = requestCalls.get();
        if (calls == null) {
            calls = new RequestCalls();
            requestCalls.set(calls);
        }
        recordCall(calls);
        return acceptanceFixture(review, answers);
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

    private AcceptanceModelOutput acceptanceFixture(ReviewResponse review, List<ConfirmedAnswer> answers) {
        return switch (scenario) {
            case "workflow-empty" -> new AcceptanceModelOutput(
                    List.of(),
                    List.of("当前需求没有足够的规则生成验收用例，请补充业务边界后再确认。"));
            case "acceptance-bad-basis" -> new AcceptanceModelOutput(
                    List.of(new AcceptanceCaseDraft(
                            "没有有效依据的用例",
                            "需求已提交。",
                            "执行操作。",
                            "得到结果。",
                            List.of(new AcceptanceBasisDraft(
                                    EvidenceSourceType.ANSWER,
                                    "Q999",
                                    "不存在的回答")))),
                    List.of());
            case "workflow-partial" -> acceptanceForAnswers(review, answers.stream().limit(1).toList());
            case "workflow-all", "workflow" -> acceptanceForAnswers(review, answers);
            default -> throw new AiModelUnavailableException();
        };
    }

    private AcceptanceModelOutput acceptanceForAnswers(
            ReviewResponse review,
            List<ConfirmedAnswer> answers) {
        if (answers.isEmpty()) {
            return new AcceptanceModelOutput(
                    List.of(),
                    List.of("尚未收到已确认回答，未把待确认规则写成确定预期。"));
        }

        List<AcceptanceCaseDraft> cases = answers.stream()
                .map(answer -> caseForAnswer(review, answer))
                .toList();
        return new AcceptanceModelOutput(cases, List.of());
    }

    private AcceptanceCaseDraft caseForAnswer(ReviewResponse review, ConfirmedAnswer answer) {
        ReviewQuestionResponse question = review.questions().stream()
                .filter(candidate -> candidate.questionId().equals(answer.questionId()))
                .findFirst()
                .orElseThrow();
        String paragraphText = review.paragraphs().stream()
                .filter(paragraph -> paragraph.id().equals(question.paragraphId()))
                .findFirst()
                .orElseThrow()
                .text();

        boolean firstQuestion = "Q001".equals(answer.questionId());
        return new AcceptanceCaseDraft(
                firstQuestion ? "跨已确认边界执行" : "按已确认的新用户规则处理",
                firstQuestion ? "用户已经完成一次领取。" : "用户满足原文描述的新用户条件。",
                firstQuestion ? "用户跨过已确认的重置边界后再次领取。" : "用户申请额外领取优惠券。",
                firstQuestion ? "系统按照用户确认的重置规则处理。" : "系统按照用户确认的新用户规则处理。",
                List.of(
                        new AcceptanceBasisDraft(
                                EvidenceSourceType.ORIGINAL,
                                question.paragraphId(),
                                paragraphText),
                        new AcceptanceBasisDraft(
                                EvidenceSourceType.ANSWER,
                                answer.questionId(),
                                answer.text())));
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
