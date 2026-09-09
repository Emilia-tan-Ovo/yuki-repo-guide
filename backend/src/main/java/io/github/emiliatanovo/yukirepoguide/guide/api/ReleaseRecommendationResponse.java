package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseRecommendationResponse(
		GuideResponse.Releases releases,
		Map<String, GuideResponse.Evidence> evidence) {

	public static ReleaseRecommendationResponse from(ReleaseSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReleaseRecommendationResponse(
				GuideResponse.Releases.from(section), evidence);
	}
}
