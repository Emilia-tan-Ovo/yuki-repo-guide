package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.util.Map;

public record ProjectGuide(
		RepositoryFacts repository,
		String repositoryEvidenceId,
		ReadmeSection readme,
		LanguageSection languages,
		ReleaseSection releases,
		Map<String, GuideEvidence> evidence,
		String explanationInputId,
        io.github.emiliatanovo.yukirepoguide.guide.experience.ExperienceReferences experience) {

    public ProjectGuide(RepositoryFacts repository, String repositoryEvidenceId, ReadmeSection readme,
            LanguageSection languages, ReleaseSection releases, Map<String, GuideEvidence> evidence, String explanationInputId) {
        this(repository, repositoryEvidenceId, readme, languages, releases, evidence, explanationInputId, null);
    }

	public ProjectGuide(RepositoryFacts repository, String repositoryEvidenceId, ReadmeSection readme,
			LanguageSection languages, ReleaseSection releases, Map<String, GuideEvidence> evidence) {
		this(repository, repositoryEvidenceId, readme, languages, releases, evidence, null);
	}

	public ProjectGuide {
		evidence = Map.copyOf(evidence);
	}
}
