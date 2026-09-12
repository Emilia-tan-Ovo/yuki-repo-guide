package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import java.util.List;
import java.util.Map;

public record ExplanationResult(Status status, String introduction, List<String> evidenceIds,
        Map<String, ExplanationInput.Evidence> evidence, String code, Long retryAfterSeconds) {
    public enum Status { AVAILABLE, INSUFFICIENT_EVIDENCE, UNAVAILABLE }
    public ExplanationResult {
        evidenceIds = List.copyOf(evidenceIds);
        evidence = Map.copyOf(evidence);
    }
    public static ExplanationResult insufficient() {
        return new ExplanationResult(Status.INSUFFICIENT_EVIDENCE, null, List.of(), Map.of(), null, null);
    }
    public static ExplanationResult unavailable(String code, Long retryAfter) {
        return new ExplanationResult(Status.UNAVAILABLE, null, List.of(), Map.of(), code, retryAfter);
    }
}
