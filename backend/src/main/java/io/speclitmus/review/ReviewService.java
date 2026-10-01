package io.speclitmus.review;

import io.speclitmus.ai.AiGateway;
import io.speclitmus.api.ParagraphResponse;
import io.speclitmus.api.ReviewMetadata;
import io.speclitmus.api.ReviewQuestionResponse;
import io.speclitmus.api.ReviewRequestException;
import io.speclitmus.api.ReviewResponse;
import io.speclitmus.requirement.ParagraphSnapshot;
import io.speclitmus.requirement.RequirementParser;
import io.speclitmus.requirement.RequirementSnapshot;
import io.speclitmus.session.ReviewSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class ReviewService {

    private static final int MAX_REQUIREMENT_LENGTH = 2_000;
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);

    private final RequirementParser requirementParser;
    private final AiGateway aiGateway;
    private final ReviewSessionStore sessionStore;
    private final EvidenceValidator evidenceValidator;
    private final String promptVersion;

    /** Kept for focused service tests and callers that do not use Spring wiring. */
    public ReviewService(
            RequirementParser requirementParser,
            AiGateway aiGateway,
            ReviewSessionStore sessionStore,
            @Value("${speclitmus.ai.review-prompt-version:review-v2}") String promptVersion) {
        this(requirementParser, aiGateway, sessionStore, new EvidenceValidator(), promptVersion);
    }

    @Autowired
    public ReviewService(
            RequirementParser requirementParser,
            AiGateway aiGateway,
            ReviewSessionStore sessionStore,
            EvidenceValidator evidenceValidator,
            @Value("${speclitmus.ai.review-prompt-version:review-v2}") String promptVersion) {
        this.requirementParser = requirementParser;
        this.aiGateway = aiGateway;
        this.sessionStore = sessionStore;
        this.evidenceValidator = evidenceValidator;
        this.promptVersion = promptVersion;
    }

    public ReviewResponse review(String requirementText) {
        if (requirementText == null || requirementText.isBlank()) {
            throw new ReviewRequestException("需求文本不能为空。");
        }
        if (requirementText.length() > MAX_REQUIREMENT_LENGTH) {
            throw new ReviewRequestException("需求文本不能超过 2,000 个字符。");
        }

        RequirementSnapshot snapshot = requirementParser.parse(requirementText);
        List<ValidatedReviewQuestion> validatedQuestions;
        try {
            // The gateway's configured SDK retry budget applies inside this one
            // call. At the business layer there is deliberately only one
            // optional repair call, and no loop that could create a third call.
            validatedQuestions = checkedDrafts(aiGateway.review(snapshot), snapshot.paragraphs());
        } catch (InvalidModelOutputException exception) {
            logger.warn("Model review output was invalid; requesting one repair: {}", exception.getMessage());
            validatedQuestions = checkedDrafts(
                    aiGateway.repairReview(snapshot, exception.getMessage()), snapshot.paragraphs());
        }

        List<ParagraphResponse> paragraphs = snapshot.paragraphs().stream()
                .map(this::toParagraphResponse)
                .toList();
        List<ValidatedReviewQuestion> finalValidatedQuestions = validatedQuestions;
        List<ReviewQuestionResponse> questions = IntStream.range(0, finalValidatedQuestions.size())
                .mapToObj(index -> toQuestionResponse(finalValidatedQuestions.get(index), index))
                .toList();

        ReviewResponse response = new ReviewResponse(
                UUID.randomUUID().toString(),
                snapshot.requirementText(),
                snapshot.requirementHash(),
                paragraphs,
                questions,
                new ReviewMetadata(aiGateway.modelName(), promptVersion)
        );
        sessionStore.save(response);
        return response;
    }

    private List<ValidatedReviewQuestion> checkedDrafts(
            List<ReviewQuestionDraft> drafts,
            List<ParagraphSnapshot> paragraphs) {
        return evidenceValidator.validate(drafts, paragraphs);
    }

    private ParagraphResponse toParagraphResponse(ParagraphSnapshot paragraph) {
        return new ParagraphResponse(paragraph.id(), paragraph.text(), paragraph.start(), paragraph.end());
    }

    private ReviewQuestionResponse toQuestionResponse(ValidatedReviewQuestion validatedQuestion, int index) {
        ReviewQuestionDraft draft = validatedQuestion.draft();
        return new ReviewQuestionResponse(
                "Q%03d".formatted(index + 1),
                draft.type(),
                draft.paragraphId(),
                draft.quote(),
                validatedQuestion.quoteStart(),
                validatedQuestion.quoteEnd(),
                draft.scenario(),
                draft.question(),
                draft.possibleInterpretations()
        );
    }
}
