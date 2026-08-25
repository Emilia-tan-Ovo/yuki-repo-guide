package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Map;

public record RepositoryLanguageBytes(Map<String, Long> bytesByLanguage) {

	public RepositoryLanguageBytes {
		bytesByLanguage = Map.copyOf(bytesByLanguage);
	}

	public static RepositoryLanguageBytes empty() {
		return new RepositoryLanguageBytes(Map.of());
	}
}
