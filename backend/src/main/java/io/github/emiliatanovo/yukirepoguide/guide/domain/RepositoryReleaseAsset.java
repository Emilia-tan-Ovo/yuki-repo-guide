package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record RepositoryReleaseAsset(
		long id,
		String name,
		long sizeBytes,
		String downloadUrl) {
}
