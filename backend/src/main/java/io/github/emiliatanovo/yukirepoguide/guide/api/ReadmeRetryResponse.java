package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSection;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReadmeRetryResponse(
		GuideResponse.Readme readme,
		Map<String, GuideResponse.Evidence> evidence) {

	public static ReadmeRetryResponse from(ReadmeSection section) {
		Map<String, GuideResponse.Evidence> evidence = new LinkedHashMap<>();
		section.evidence().forEach((id, value) ->
				evidence.put(id, GuideResponse.Evidence.from(value)));
		return new ReadmeRetryResponse(GuideResponse.Readme.from(section), evidence);
	}
}
