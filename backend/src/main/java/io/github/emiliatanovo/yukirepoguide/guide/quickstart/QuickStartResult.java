package io.github.emiliatanovo.yukirepoguide.guide.quickstart;

import java.util.*;

public record QuickStartResult(Status status, ContentStatus contentStatus,
        List<Item> requirements, List<Item> steps, List<Item> configuration, List<Item> cautions,
        List<Item> gaps, Map<String, QuickStartInput.Evidence> evidence, String code, Long retryAfterSeconds, String resultId) {
    public QuickStartResult(Status status, ContentStatus contentStatus, List<Item> requirements, List<Item> steps,
            List<Item> configuration, List<Item> cautions, List<Item> gaps, Map<String, QuickStartInput.Evidence> evidence,
            String code, Long retryAfterSeconds) {
        this(status, contentStatus, requirements, steps, configuration, cautions, gaps, evidence, code, retryAfterSeconds, null);
    }
    public QuickStartResult withResultId(String id) {
        return new QuickStartResult(status, contentStatus, requirements, steps, configuration, cautions, gaps,
                evidence, code, retryAfterSeconds, id);
    }
    public enum Status { AVAILABLE, UNAVAILABLE }
    public enum ContentStatus { COMPLETE, INCOMPLETE, NOT_PROVIDED }
    public record Block(String evidenceId, String text) {}
    public record Item(String text, List<String> evidenceIds, List<Block> blocks) {
        public Item { evidenceIds = List.copyOf(evidenceIds); blocks = List.copyOf(blocks); }
    }
    public QuickStartResult {
        requirements = List.copyOf(requirements); steps = List.copyOf(steps);
        configuration = List.copyOf(configuration); cautions = List.copyOf(cautions); gaps = List.copyOf(gaps);
        evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }
    public static QuickStartResult notProvided() {
        return new QuickStartResult(Status.AVAILABLE, ContentStatus.NOT_PROVIDED,
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), null, null);
    }
    public static QuickStartResult unavailable(String code, Long retryAfter) {
        return new QuickStartResult(Status.UNAVAILABLE, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), code, retryAfter);
    }
}
