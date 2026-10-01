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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ReviewService {

    private static final int MAX_QUESTIONS = 5;

    private final RequirementParser requirementParser;
    private final AiGateway aiGateway;
    private final ReviewSessionStore sessionStore;
    private final String promptVersion;

    public ReviewService(
            RequirementParser requirementParser,
            AiGateway aiGateway,
            ReviewSessionStore sessionStore,
            @Value("${speclitmus.ai.review-prompt-version:review-v1}") String promptVersion) {
        this.requirementParser = requirementParser;
        this.aiGateway = aiGateway;
        this.sessionStore = sessionStore;
        this.promptVersion = promptVersion;
    }

    public ReviewResponse review(String requirementText) {
        if (requirementText == null || requirementText.isBlank()) {
            throw new ReviewRequestException("需求文本不能为空。");
        }

        RequirementSnapshot snapshot = requirementParser.parse(requirementText);
        List<ReviewQuestionDraft> drafts = aiGateway.review(snapshot);
        validateDrafts(drafts, snapshot.paragraphs());

        List<ParagraphResponse> paragraphs = snapshot.paragraphs().stream()
                .map(this::toParagraphResponse)
                .toList();
        List<ReviewQuestionResponse> questions = IntStream.range(0, drafts.size())
                .mapToObj(index -> toQuestionResponse(drafts.get(index), index))
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

    private void validateDrafts(List<ReviewQuestionDraft> drafts, List<ParagraphSnapshot> paragraphs) {
        if (drafts == null || drafts.size() > MAX_QUESTIONS) {
            throw new InvalidModelOutputException("Model returned too many or missing review questions");
        }
        Set<String> paragraphIds = paragraphs.stream().map(ParagraphSnapshot::id).collect(Collectors.toSet());
        for (ReviewQuestionDraft draft : drafts) {
            if (draft == null || draft.type() == null || !paragraphIds.contains(draft.paragraphId())) {
                throw new InvalidModelOutputException("Model returned an invalid review question");
            }
            requireText(draft.quote(), 200, "quote");
            requireText(draft.scenario(), 500, "scenario");
            requireText(draft.question(), 300, "question");
            if (draft.possibleInterpretations() == null || draft.possibleInterpretations().size() > 3) {
                throw new InvalidModelOutputException("Model returned invalid possible interpretations");
            }
            draft.possibleInterpretations().forEach(value -> requireText(value, 200, "possible interpretation"));
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidModelOutputException("Model returned an invalid " + field);
        }
    }

    private ParagraphResponse toParagraphResponse(ParagraphSnapshot paragraph) {
        return new ParagraphResponse(paragraph.id(), paragraph.text(), paragraph.start(), paragraph.end());
    }

    private ReviewQuestionResponse toQuestionResponse(ReviewQuestionDraft draft, int index) {
        return new ReviewQuestionResponse(
                "Q%03d".formatted(index + 1),
                draft.type(),
                draft.paragraphId(),
                draft.quote(),
                null,
                null,
                draft.scenario(),
                draft.question(),
                draft.possibleInterpretations()
        );
    }
}
