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
    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);

    private final RequirementParser requirementParser;
    private final AiGateway aiGateway;
    private final ReviewSessionStore sessionStore;
    private final String promptVersion;

    public ReviewService(
            RequirementParser requirementParser,
            AiGateway aiGateway,
            ReviewSessionStore sessionStore,
            @Value("${speclitmus.ai.review-prompt-version:review-v2}") String promptVersion) {
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
        List<ReviewQuestionDraft> drafts;
        try {
            drafts = checkedDrafts(aiGateway.review(snapshot), snapshot.paragraphs());
        } catch (InvalidModelOutputException exception) {
            logger.warn("Model review output was invalid; requesting one repair: {}", exception.getMessage());
            drafts = checkedDrafts(
                    aiGateway.repairReview(snapshot, exception.getMessage()), snapshot.paragraphs());
        }
        List<ReviewQuestionDraft> validatedDrafts = drafts;

        List<ParagraphResponse> paragraphs = snapshot.paragraphs().stream()
                .map(this::toParagraphResponse)
                .toList();
        List<ReviewQuestionResponse> questions = IntStream.range(0, validatedDrafts.size())
                .mapToObj(index -> toQuestionResponse(validatedDrafts.get(index), index))
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

    private List<ReviewQuestionDraft> checkedDrafts(
            List<ReviewQuestionDraft> drafts,
            List<ParagraphSnapshot> paragraphs) {
        List<ReviewQuestionDraft> repaired = repairParagraphIds(drafts, paragraphs);
        validateDrafts(repaired, paragraphs);
        return repaired;
    }

    private List<ReviewQuestionDraft> repairParagraphIds(
            List<ReviewQuestionDraft> drafts,
            List<ParagraphSnapshot> paragraphs) {
        if (drafts == null) {
            return null;
        }
        Set<String> paragraphIds = paragraphs.stream().map(ParagraphSnapshot::id).collect(Collectors.toSet());
        return IntStream.range(0, drafts.size())
                .mapToObj(index -> repairParagraphId(drafts.get(index), index, paragraphs, paragraphIds))
                .toList();
    }

    private ReviewQuestionDraft repairParagraphId(
            ReviewQuestionDraft draft,
            int index,
            List<ParagraphSnapshot> paragraphs,
            Set<String> paragraphIds) {
        if (draft == null || draft.quote() == null || draft.quote().isBlank()) {
            return draft;
        }

        if (paragraphIds.contains(draft.paragraphId()) && paragraphs.stream()
                .anyMatch(paragraph -> paragraph.id().equals(draft.paragraphId())
                        && paragraph.text().contains(draft.quote()))) {
            return draft;
        }

        List<ParagraphSnapshot> matches = paragraphs.stream()
                .filter(paragraph -> paragraph.text().contains(draft.quote()))
                .toList();
        if (matches.isEmpty()) {
            throw new InvalidModelOutputException(
                    "Model quote matched no paragraph at position " + (index + 1));
        }
        if (matches.size() > 1) {
            throw new InvalidModelOutputException(
                    "Model quote matched multiple paragraphs with no valid paragraph ID at position " + (index + 1));
        }

        logger.warn("Corrected a model paragraph ID using a unique quote match at position {}", index + 1);
        ParagraphSnapshot match = matches.getFirst();
        return new ReviewQuestionDraft(
                draft.type(),
                match.id(),
                draft.quote(),
                draft.scenario(),
                draft.question(),
                draft.possibleInterpretations()
        );
    }

    private void validateDrafts(List<ReviewQuestionDraft> drafts, List<ParagraphSnapshot> paragraphs) {
        if (drafts == null || drafts.size() > MAX_QUESTIONS) {
            throw new InvalidModelOutputException("Model returned too many or missing review questions");
        }
        Set<String> paragraphIds = paragraphs.stream().map(ParagraphSnapshot::id).collect(Collectors.toSet());
        for (int index = 0; index < drafts.size(); index++) {
            ReviewQuestionDraft draft = drafts.get(index);
            int questionNumber = index + 1;
            if (draft == null) {
                throw new InvalidModelOutputException("Model returned a missing review question at position " + questionNumber);
            }
            if (draft.type() == null) {
                throw new InvalidModelOutputException("Model returned a review question without a type at position " + questionNumber);
            }
            if (draft.paragraphId() == null || draft.paragraphId().isBlank()) {
                throw new InvalidModelOutputException("Model returned a review question without a paragraph ID at position " + questionNumber);
            }
            if (!paragraphIds.contains(draft.paragraphId())) {
                throw new InvalidModelOutputException("Model returned a review question with an unknown paragraph ID at position " + questionNumber);
            }
            requireText(draft.quote(), 200, "quote");
            ParagraphSnapshot paragraph = paragraphs.stream()
                    .filter(value -> value.id().equals(draft.paragraphId()))
                    .findFirst().orElseThrow();
            if (!paragraph.text().contains(draft.quote())) {
                throw new InvalidModelOutputException("Model quote was not found in its paragraph at position " + questionNumber);
            }
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
