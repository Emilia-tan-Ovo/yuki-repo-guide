package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RuntimeEnvironment;
import io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExperienceRequest(@NotBlank @Size(max = 100) String guideId,
        @NotBlank @Size(max = 100) String readmeResultId,
        @NotBlank @Size(max = 100) String releasesResultId,
        @Size(max = 100) String quickStartResultId, ReleaseRecommendationRequest.ConfirmedRuntime runtime) {
    public ExperienceReferences references() {
        return new ExperienceReferences(guideId, readmeResultId, releasesResultId, quickStartResultId);
    }
    public RuntimeEnvironment confirmedRuntime() {
        return runtime == null ? null : new ReleaseRecommendationRequest(null, runtime, null).confirmedRuntime();
    }
}
