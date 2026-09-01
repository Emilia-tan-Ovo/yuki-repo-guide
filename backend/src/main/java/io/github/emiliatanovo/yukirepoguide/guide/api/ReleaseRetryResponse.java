package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseRetryResponse(
		GuideResponse.Releases releases,
		Map<String, GuideResponse.Evidence> evidence) {

	public static ReleaseRetryResponse from(ReleaseSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReleaseRetryResponse(GuideResponse.Releases.from(section), evidence);
	}
}
