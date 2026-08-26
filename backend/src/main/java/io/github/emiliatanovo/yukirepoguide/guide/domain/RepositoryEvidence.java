package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record RepositoryEvidence(
		String id,
		String source,
		String repositoryUrl,
		RawFact recentCodeUpdate) implements GuideEvidence {
}
