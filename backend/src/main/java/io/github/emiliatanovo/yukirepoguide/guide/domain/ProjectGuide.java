package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Map;

public record ProjectGuide(
		RepositoryFacts repository,
		String repositoryEvidenceId,
		LanguageSection languages,
		Map<String, GuideEvidence> evidence) {

	public ProjectGuide {
		evidence = Map.copyOf(evidence);
	}
}
