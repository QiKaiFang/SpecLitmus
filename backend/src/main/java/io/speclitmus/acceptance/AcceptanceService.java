package io.speclitmus.acceptance;

import io.speclitmus.ai.AcceptanceAiGateway;
import io.speclitmus.api.AcceptanceBasisResponse;
import io.speclitmus.api.AcceptanceCaseResponse;
import io.speclitmus.api.AcceptanceRequest;
import io.speclitmus.api.AcceptanceResponse;
import io.speclitmus.api.AnswerRequest;
import io.speclitmus.api.ReviewMetadata;
import io.speclitmus.api.ReviewRequestException;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.review.InvalidModelOutputException;
import io.speclitmus.session.ReviewSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class AcceptanceService {

    private static final int MAX_ANSWER_LENGTH = 500;
    private static final Logger logger = LoggerFactory.getLogger(AcceptanceService.class);

    private final ReviewSessionStore sessionStore;
    private final AcceptanceAiGateway aiGateway;
    private final AcceptanceEvidenceValidator evidenceValidator;
    private final String promptVersion;

    public AcceptanceService(
            ReviewSessionStore sessionStore,
            AcceptanceAiGateway aiGateway,
            AcceptanceEvidenceValidator evidenceValidator,
            @Value("${speclitmus.ai.acceptance-prompt-version:acceptance-v1}") String promptVersion) {
        this.sessionStore = sessionStore;
        this.aiGateway = aiGateway;
        this.evidenceValidator = evidenceValidator;
        this.promptVersion = promptVersion;
    }

    public AcceptanceResponse generate(String reviewId, AcceptanceRequest request) {
        ReviewResponse review = sessionStore.find(reviewId)
                .orElseThrow(SessionExpiredException::new)
                .review();
        List<ConfirmedAnswer> answers = validateAnswers(request, review);

        AcceptanceModelOutput output;
        try {
            output = checkedOutput(aiGateway.acceptance(review, answers), review, answers);
        } catch (InvalidModelOutputException exception) {
            logger.warn("Model acceptance output was invalid; requesting one repair: {}", exception.getMessage());
            output = checkedOutput(
                    aiGateway.repairAcceptance(review, answers, exception.getMessage()), review, answers);
        }

        Set<String> confirmedIds = answers.stream()
                .map(ConfirmedAnswer::questionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> pendingQuestionIds = review.questions().stream()
                .map(question -> question.questionId())
                .filter(questionId -> !confirmedIds.contains(questionId))
                .toList();

        AcceptanceModelOutput validatedOutput = output;
        List<AcceptanceCaseResponse> cases = IntStream.range(0, validatedOutput.cases().size())
                .mapToObj(index -> toCaseResponse(validatedOutput.cases().get(index), index))
                .toList();
        return new AcceptanceResponse(
                cases,
                pendingQuestionIds,
                validatedOutput.warnings(),
                new ReviewMetadata(aiGateway.modelName(), promptVersion));
    }

    private List<ConfirmedAnswer> validateAnswers(AcceptanceRequest request, ReviewResponse review) {
        if (request == null || request.answers() == null) {
            throw new ReviewRequestException("answers 不能为空。");
        }

        Set<String> validQuestionIds = review.questions().stream()
                .map(question -> question.questionId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> seen = new HashSet<>();
        List<ConfirmedAnswer> confirmed = new ArrayList<>();

        for (AnswerRequest answer : request.answers()) {
            if (answer == null || answer.questionId() == null || answer.questionId().isBlank()
                    || answer.text() == null) {
                throw new ReviewRequestException("回答必须包含 questionId 和 text。");
            }
            if (!validQuestionIds.contains(answer.questionId())) {
                throw new ReviewRequestException("回答包含当前审查中不存在的问题 ID。");
            }
            if (!seen.add(answer.questionId())) {
                throw new ReviewRequestException("同一个问题不能重复提交回答。");
            }
            if (answer.text().length() > MAX_ANSWER_LENGTH) {
                throw new ReviewRequestException("单个回答不能超过 500 个字符。");
            }
            if (!answer.text().isBlank()) {
                confirmed.add(new ConfirmedAnswer(answer.questionId(), answer.text()));
            }
        }
        return List.copyOf(confirmed);
    }

    private AcceptanceModelOutput checkedOutput(
            AcceptanceModelOutput output,
            ReviewResponse review,
            List<ConfirmedAnswer> answers) {
        evidenceValidator.validate(output, review, answers);
        return output;
    }

    private AcceptanceCaseResponse toCaseResponse(AcceptanceCaseDraft draft, int index) {
        List<AcceptanceBasisResponse> basis = draft.basis().stream()
                .map(item -> new AcceptanceBasisResponse(item.sourceType(), item.sourceId(), item.quote()))
                .toList();
        return new AcceptanceCaseResponse(
                "C%03d".formatted(index + 1),
                draft.title(),
                draft.given(),
                draft.when(),
                draft.then(),
                basis);
    }
}
