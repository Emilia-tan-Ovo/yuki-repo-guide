package io.github.emiliatanovo.yukirepoguide.guide.domain;

public sealed interface GuideEvidence permits LanguageEvidence, ReadmeEvidence, ReleaseAssetEvidence,
		ReleaseEvidence, RepositoryEvidence {

	String id();

	String source();
}
