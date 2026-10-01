package io.speclitmus.requirement;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RequirementParser {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("(?:\\n[ \\t]*){2,}");

    public RequirementSnapshot parse(String requirementText) {
        if (requirementText == null) {
            throw new IllegalArgumentException("requirementText is required");
        }

        String normalizedText = requirementText.replace("\r\n", "\n").replace('\r', '\n');
        List<ParagraphSnapshot> paragraphs = splitParagraphs(normalizedText);
        return new RequirementSnapshot(normalizedText, sha256(normalizedText), paragraphs);
    }

    private List<ParagraphSnapshot> splitParagraphs(String text) {
        List<ParagraphSnapshot> paragraphs = new ArrayList<>();
        Matcher matcher = PARAGRAPH_SEPARATOR.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addParagraph(text, start, matcher.start(), paragraphs);
            start = matcher.end();
        }
        addParagraph(text, start, text.length(), paragraphs);
        return List.copyOf(paragraphs);
    }

    private void addParagraph(String text, int start, int end, List<ParagraphSnapshot> paragraphs) {
        String paragraphText = text.substring(start, end);
        if (!paragraphText.isBlank()) {
            String id = "P%03d".formatted(paragraphs.size() + 1);
            paragraphs.add(new ParagraphSnapshot(id, paragraphText, start, end));
        }
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
