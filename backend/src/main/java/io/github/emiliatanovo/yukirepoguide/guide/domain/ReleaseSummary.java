package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.time.Instant;
import java.util.List;

public record ReleaseSummary(
		String name,
		String tagName,
		Instant publishedAt,
		List<ReleaseAsset> assets,
		int reportedAssetCount,
		int excludedAssetCount,
		boolean assetsTruncated,
		List<ReleaseWarning> warnings,
		String evidenceId) {

	public ReleaseSummary {
		assets = List.copyOf(assets);
		warnings = List.copyOf(warnings);
	}
}
