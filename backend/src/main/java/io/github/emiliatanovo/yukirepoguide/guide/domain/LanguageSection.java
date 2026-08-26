package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;

public record LanguageSection(
		LanguageSectionStatus status,
		List<LanguageShare> items,
		LanguageFailure failure,
		String evidenceId,
		LanguageEvidence evidence) {

	public LanguageSection {
		items = List.copyOf(items);
	}

	public static LanguageSection notProvided() {
		return new LanguageSection(LanguageSectionStatus.NOT_PROVIDED, List.of(), null, null, null);
	}

	public static LanguageSection available(List<LanguageShare> items, LanguageEvidence evidence) {
		return new LanguageSection(
				LanguageSectionStatus.AVAILABLE, items, null, evidence.id(), evidence);
	}

	public static LanguageSection failed(GuideErrorCode code, Long retryAfterSeconds) {
		return new LanguageSection(
				LanguageSectionStatus.FAILED,
				List.of(),
				new LanguageFailure(code, retryAfterSeconds),
				null,
				null);
	}
}
