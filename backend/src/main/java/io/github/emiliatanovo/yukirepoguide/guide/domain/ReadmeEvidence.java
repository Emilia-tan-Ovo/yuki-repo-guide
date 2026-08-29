package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReadmeEvidence(
		String id,
		String source,
		String readmeUrl,
		String path,
		String sha,
		String context) implements GuideEvidence {
}
