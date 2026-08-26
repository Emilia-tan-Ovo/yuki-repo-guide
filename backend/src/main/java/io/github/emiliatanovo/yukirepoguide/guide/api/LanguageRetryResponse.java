package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSection;

import java.util.Map;

public record LanguageRetryResponse(
		GuideResponse.Languages languages,
		Map<String, GuideResponse.Evidence> evidence) {

	public static LanguageRetryResponse from(LanguageSection section) {
		Map<String, GuideResponse.Evidence> evidence = section.evidence() == null
				? Map.of()
				: Map.of(section.evidenceId(), GuideResponse.Evidence.from(section.evidence()));
		return new LanguageRetryResponse(GuideResponse.Languages.from(section), evidence);
	}
}
