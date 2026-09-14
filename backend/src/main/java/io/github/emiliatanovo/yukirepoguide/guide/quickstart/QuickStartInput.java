package io.github.emiliatanovo.yukirepoguide.guide.quickstart;

import java.util.*;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSectionStatus;

public record QuickStartInput(ReadmeSectionStatus sourceStatus, boolean truncated, Map<String, Evidence> evidence) {
    public QuickStartInput { evidence = Collections.unmodifiableMap(new LinkedHashMap<>(evidence)); }
    public enum Kind { TEXT, COMMAND, CONFIGURATION, CODE }
    public record Evidence(String id, Kind kind, String sourceUrl, String path, String sha,
            String section, int order, String text) {}
}
