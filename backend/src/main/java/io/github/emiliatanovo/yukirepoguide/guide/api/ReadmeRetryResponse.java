package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReadmeRetryResponse(
		GuideResponse.Readme readme,
		Map<String, GuideResponse.Evidence> evidence, String resultId) {

    public ReadmeRetryResponse(GuideResponse.Readme readme, Map<String, GuideResponse.Evidence> evidence) {
        this(readme, evidence, null);
    }

    public static ReadmeRetryResponse from(io.github.emiliatanovo.yukirepoguide.guide.experience.RegisteredRegion<ReadmeSection> region) {
        var response = from(region.value());
        return new ReadmeRetryResponse(response.readme(), response.evidence(), region.resultId());
    }

	public static ReadmeRetryResponse from(ReadmeSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReadmeRetryResponse(GuideResponse.Readme.from(section), evidence);
	}
}
