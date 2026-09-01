package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.time.Instant;
import java.util.List;

public record RepositoryRelease(
		long id,
		String name,
		String tagName,
		String releaseUrl,
		Instant publishedAt,
		boolean draft,
		boolean prerelease,
		int reportedAssetCount,
		int excludedAssetCount,
		List<RepositoryReleaseAsset> assets) {

	public RepositoryRelease {
		assets = List.copyOf(assets);
	}
}
