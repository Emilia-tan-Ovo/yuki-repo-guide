package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReleaseAsset(
		String name,
		long sizeBytes,
		String downloadUrl,
		String evidenceId) {
}
