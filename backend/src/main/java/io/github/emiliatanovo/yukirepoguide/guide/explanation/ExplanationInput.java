package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import java.util.Map;

public record ExplanationInput(String repositoryName, Map<String, Evidence> evidence) {
    public ExplanationInput { evidence = Map.copyOf(evidence); }
    public record Evidence(String id, String sourceUrl, String path, String sha, String text) {}
}
