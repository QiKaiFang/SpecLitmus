package io.speclitmus.api;

import io.speclitmus.acceptance.EvidenceSourceType;

public record AcceptanceBasisResponse(EvidenceSourceType sourceType, String sourceId, String quote) {
}
