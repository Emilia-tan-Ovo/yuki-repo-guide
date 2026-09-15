package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReleaseSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseRetryResponse(
		GuideResponse.Releases releases,
		Map<String, GuideResponse.Evidence> evidence, String resultId) {

    public ReleaseRetryResponse(GuideResponse.Releases releases, Map<String, GuideResponse.Evidence> evidence) {
        this(releases, evidence, null);
    }

    public static ReleaseRetryResponse from(io.github.emiliatanovo.yukirepoguide.guide.experience.RegisteredRegion<ReleaseSection> region) {
        var response = from(region.value());
        return new ReleaseRetryResponse(response.releases(), response.evidence(), region.resultId());
    }

	public static ReleaseRetryResponse from(ReleaseSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReleaseRetryResponse(GuideResponse.Releases.from(section), evidence);
	}
}
