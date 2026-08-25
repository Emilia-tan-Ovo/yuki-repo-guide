package io.github.emiliatanovo.yukirepoguide.guide.domain;

public sealed interface GuideEvidence permits LanguageEvidence, RepositoryEvidence {

	String id();

	String source();
}
