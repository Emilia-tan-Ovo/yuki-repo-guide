package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import java.time.Duration;

/** Provider boundary: complete within remaining time; no session, snapshot ID or tools. */
@FunctionalInterface
public interface ExplanationModel {
    String generate(ExplanationInput input, boolean correction, Duration remaining);
}
