package io.speclitmus.requirement;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class RequirementParser {

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
        int paragraphStart = -1;
        int paragraphEnd = -1;
        int lineStart = 0;

        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }

            String line = text.substring(lineStart, lineEnd);
            if (line.isBlank()) {
                if (paragraphStart >= 0) {
                    addParagraph(text, paragraphStart, paragraphEnd, paragraphs);
                    paragraphStart = -1;
                    paragraphEnd = -1;
                }
            } else {
                if (paragraphStart < 0) {
                    paragraphStart = lineStart;
                }
                paragraphEnd = lineEnd;
            }

            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }

        if (paragraphStart >= 0) {
            addParagraph(text, paragraphStart, paragraphEnd, paragraphs);
        }
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
