package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseRecommendationResponse(
		GuideResponse.Releases releases,
		Map<String, GuideResponse.Evidence> evidence, String resultId) {

    public ReleaseRecommendationResponse(GuideResponse.Releases releases, Map<String, GuideResponse.Evidence> evidence) {
        this(releases, evidence, null);
    }

    public static ReleaseRecommendationResponse from(io.github.emiliatanovo.yukirepoguide.guide.experience.RegisteredRegion<ReleaseSection> region) {
        var response = from(region.value());
        return new ReleaseRecommendationResponse(response.releases(), response.evidence(), region.resultId());
    }

	public static ReleaseRecommendationResponse from(ReleaseSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReleaseRecommendationResponse(
				GuideResponse.Releases.from(section), evidence);
	}
}
