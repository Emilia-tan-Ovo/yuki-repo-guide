package io.github.emiliatanovo.yukirepoguide.guide.domain;

public record ReleaseAssetEvidence(
		String id,
		String source,
		String releaseEvidenceId,
		long assetId,
		String name,
		long sizeBytes,
		String downloadUrl) implements GuideEvidence {
}
