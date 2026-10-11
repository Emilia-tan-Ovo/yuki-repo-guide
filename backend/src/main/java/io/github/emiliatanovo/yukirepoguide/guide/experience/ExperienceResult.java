package io.github.emiliatanovo.yukirepoguide.guide.experience;

import java.util.List;
import java.util.Map;

public record ExperienceResult(Status status, Path primary, List<Path> alternatives,
        List<String> unresolved, Map<String, Evidence> evidence) {
    public enum Status { PRIMARY_AVAILABLE, CANDIDATES_ONLY, UNDETERMINED, NO_PATH }
    public enum Kind { ONLINE, STABLE_RELEASE, PRERELEASE, QUICK_START }
    public record Evidence(String source, String url, String detail, String path, String sha) {}
    public record Option(String label, String url, List<String> evidenceIds, List<String> warnings) {
        public Option { evidenceIds = List.copyOf(evidenceIds); warnings = List.copyOf(warnings); }
    }
    public record Path(String id, Kind kind, boolean reliable, List<Option> options, int optionCount) {
        public Path(String id, Kind kind, boolean reliable, List<Option> options) {
            this(id, kind, reliable, options, options.size());
        }
        public Path { options = options.stream().limit(50).toList(); }
    }
    public ExperienceResult {
        alternatives = List.copyOf(alternatives);
        unresolved = List.copyOf(unresolved);
        evidence = Map.copyOf(evidence);
    }
}
