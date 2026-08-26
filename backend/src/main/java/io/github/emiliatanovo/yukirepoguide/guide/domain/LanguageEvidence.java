package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.List;

public record LanguageEvidence(
		String id,
		String source,
		long totalBytes,
		List<LanguageShare> items) implements GuideEvidence {

	public LanguageEvidence {
		items = List.copyOf(items);
	}
}
