package io.github.emiliatanovo.yukirepoguide.guide.domain;

public sealed interface GuideEvidence permits LanguageEvidence, ReadmeEvidence, RepositoryEvidence {

	String id();

	String source();
}
