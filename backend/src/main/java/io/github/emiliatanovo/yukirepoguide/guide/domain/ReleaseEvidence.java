package io.github.emiliatanovo.yukirepoguide.guide.domain;

import java.time.Instant;

public record ReleaseEvidence(
		String id,
		String source,
		String releaseUrl,
		long releaseId,
		String tagName,
		Instant publishedAt,
		ReleaseChannel channel,
		int reportedAssetCount) implements GuideEvidence {
}
