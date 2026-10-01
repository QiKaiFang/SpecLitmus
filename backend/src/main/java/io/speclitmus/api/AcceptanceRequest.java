package io.speclitmus.api;

import java.util.List;

public record AcceptanceRequest(List<AnswerRequest> answers) {
}
